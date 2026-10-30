package chatmap.application.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import chatmap.application.model.ChatExportModel;
import chatmap.application.port.document.ProjectDocumentReader;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.SearchOptions;
import chatmap.domain.SearchResult;

/** Bounded, project-scoped reads for the MCP presentation adapter. */
public final class MemoryReadService {

    public MemoryReadService(SearchService searchService, ExportService exportService,
            ProjectDocumentReader documentReader, long projectId) {
        if (projectId <= 0) {
            throw new IllegalArgumentException("projectId must be positive");
        }
        this.searchService = searchService;
        this.exportService = exportService;
        this.documentReader = documentReader;
        this.projectId = projectId;
    }

    public SearchChatsResult searchChats(String query, Integer requestedLimit) throws SQLException {
        String trimmed = requireQuery(query);
        int limit = boundedLimit(requestedLimit, defaultSearchLimit, maximumSearchLimit, "limit");
        List<SearchResult> matches = searchService.searchResults(
                trimmed, new SearchOptions(projectId, null, null, false));
        List<ChatSearchHit> hits = matches.stream()
                .limit(limit)
                .map(this::toSearchHit)
                .toList();
        return new SearchChatsResult("ok", hits, matches.size(), matches.size() > limit,
                "Prefix-token OR search; snippets are navigation aids, not message citations.");
    }

    public ChatMessagesResult readChatMessages(long chatId, Integer requestedOffset,
            Integer requestedLimit, String cursor) throws SQLException {
        if (chatId <= 0) {
            throw new IllegalArgumentException("chatId must be positive");
        }
        int limit = boundedLimit(requestedLimit, defaultMessageLimit, maximumMessageLimit, "limit");
        int messageOffset = nonnegative(requestedOffset, 0, "offset");
        int characterOffset = 0;
        ChatExportModel model = exportService.loadChat(chatId).orElse(null);
        if (model == null) {
            return ChatMessagesResult.outcome("missing", chatId, "No chat exists with that id.");
        }
        if (!Long.valueOf(projectId).equals(model.chat().projectId())) {
            return ChatMessagesResult.outcome("outOfScope", chatId,
                    "The requested chat is not in the configured project.");
        }

        List<Message> messages = model.messages().stream()
                .sorted(Comparator.comparingInt(Message::sequence).thenComparingLong(Message::id))
                .toList();
        String observedHash = messageVersion(messages);
        if (cursor != null && !cursor.isBlank()) {
            MessageCursor decoded = decodeMessageCursor(cursor);
            if (decoded.chatId() != chatId) {
                throw new IllegalArgumentException("cursor belongs to a different chat");
            }
            if (!decoded.observedHash().equals(observedHash)) {
                return changedChat(model.chat(), observedHash);
            }
            messageOffset = decoded.messageOffset();
            characterOffset = decoded.characterOffset();
        }
        if (messageOffset > messages.size()) {
            throw new IllegalArgumentException("offset exceeds the message count");
        }
        if (messageOffset == messages.size()) {
            return chatResult(model.chat(), observedHash, messages.size(), List.of(), 0, null);
        }
        int firstLength = codePointLength(messages.get(messageOffset).text());
        if (characterOffset < 0 || characterOffset > firstLength) {
            throw new IllegalArgumentException("cursor character offset is outside the message");
        }
        if (firstLength > 0 && characterOffset == firstLength) {
            messageOffset++;
            characterOffset = 0;
        }
        return chunkMessages(model.chat(), messages, observedHash, messageOffset, characterOffset, limit);
    }

    public WorkingContextResult readWorkingContext(Integer requestedOffset, Integer requestedMaxChars,
            String cursor) throws IOException {
        int offset = nonnegative(requestedOffset, 0, "offset");
        int maxChars = boundedLimit(requestedMaxChars, defaultDocumentLimit, maximumDocumentLimit, "maxChars");
        ProjectDocumentReader.DocumentResult document = documentReader.readWorkingContext();
        if (document.status() != ProjectDocumentReader.DocumentResult.Status.available) {
            return new WorkingContextResult(document.status().name(), document.relativePath(), null,
                    null, 0, 0, null, false, document.detail(), workingStateLabel);
        }
        if (cursor != null && !cursor.isBlank()) {
            DocumentCursor decoded = decodeDocumentCursor(cursor);
            if (!decoded.hash().equals(document.hash())) {
                return new WorkingContextResult("changedSource", document.relativePath(), document.hash(),
                        null, 0, codePointLength(document.text()), null, false,
                        "The document changed after the supplied cursor was issued.", workingStateLabel);
            }
            offset = decoded.offset();
        }
        int totalChars = codePointLength(document.text());
        if (offset > totalChars) {
            throw new IllegalArgumentException("offset exceeds the document length");
        }
        int end = Math.min(totalChars, offset + maxChars);
        String text = codePointSubstring(document.text(), offset, end);
        String nextCursor = end < totalChars ? encodeDocumentCursor(end, document.hash()) : null;
        return new WorkingContextResult("ok", document.relativePath(), document.hash(), text,
                offset, totalChars, nextCursor, nextCursor != null, null, workingStateLabel);
    }

    private ChatMessagesResult chunkMessages(Chat chat, List<Message> messages, String observedHash,
            int startMessage, int startCharacter, int limit) {
        List<MessageChunk> chunks = new ArrayList<>();
        int remainingBudget = messageTextBudget;
        int messageIndex = startMessage;
        int characterOffset = startCharacter;
        int returnedChars = 0;
        String nextCursor = null;
        while (messageIndex < messages.size() && chunks.size() < limit && remainingBudget > 0) {
            Message message = messages.get(messageIndex);
            int totalChars = codePointLength(message.text());
            int take = Math.min(totalChars - characterOffset, remainingBudget);
            int end = characterOffset + take;
            String text = codePointSubstring(message.text(), characterOffset, end);
            chunks.add(new MessageChunk(message.id(), message.sequence(), message.role().dbValue(),
                    message.timestamp(), text, characterOffset, end, totalChars, end < totalChars));
            returnedChars += take;
            remainingBudget -= take;
            if (end < totalChars) {
                nextCursor = encodeMessageCursor(chat.id(), messageIndex, end, observedHash);
                break;
            }
            messageIndex++;
            characterOffset = 0;
        }
        if (nextCursor == null && messageIndex < messages.size()) {
            nextCursor = encodeMessageCursor(chat.id(), messageIndex, 0, observedHash);
        }
        return chatResult(chat, observedHash, messages.size(), chunks, returnedChars, nextCursor);
    }

    private ChatMessagesResult chatResult(Chat chat, String observedHash, int totalMessages,
            List<MessageChunk> chunks, int returnedChars, String nextCursor) {
        return new ChatMessagesResult("ok", chat.id(), chat.title(), chat.source().dbValue(),
                chat.externalConversationId(), chat.sourceUri(), chat.contentHash(), observedHash,
                totalMessages, chunks, returnedChars, nextCursor, nextCursor != null,
                "Stored normalized message text; raw source bytes and rawJson are not exposed.");
    }

    private ChatMessagesResult changedChat(Chat chat, String observedHash) {
        return new ChatMessagesResult("changedSource", chat.id(), chat.title(), chat.source().dbValue(),
                chat.externalConversationId(), chat.sourceUri(), chat.contentHash(), observedHash,
                0, List.of(), 0, null, false,
                "The chat changed after the supplied cursor was issued.");
    }

    private ChatSearchHit toSearchHit(SearchResult result) {
        Chat chat = result.chat();
        return new ChatSearchHit(chat.id(), chat.title(), chat.source().dbValue(), chat.createdAt(),
                chat.updatedAt(), chat.importedAt(), truncate(result.snippet(), snippetLimit),
                chat.externalConversationId(), chat.sourceUri(), chat.contentHash());
    }

    private static String requireQuery(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (codePointLength(trimmed) > queryLimit) {
            throw new IllegalArgumentException("query exceeds 256 Unicode code points");
        }
        return trimmed;
    }

    private static int boundedLimit(Integer requested, int defaultValue, int maximum, String name) {
        int value = requested == null ? defaultValue : requested;
        if (value <= 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be between 1 and " + maximum);
        }
        return value;
    }

    private static int nonnegative(Integer requested, int defaultValue, String name) {
        int value = requested == null ? defaultValue : requested;
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static String truncate(String text, int maximumCodePoints) {
        if (text == null || codePointLength(text) <= maximumCodePoints) {
            return text;
        }
        return codePointSubstring(text, 0, maximumCodePoints);
    }

    private static int codePointLength(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String codePointSubstring(String text, int start, int end) {
        int startIndex = text.offsetByCodePoints(0, start);
        int endIndex = text.offsetByCodePoints(0, end);
        return text.substring(startIndex, endIndex);
    }

    private static String messageVersion(List<Message> messages) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                for (Message message : messages) {
                    data.writeLong(message.id());
                    data.writeInt(message.sequence());
                    writeString(data, message.role().dbValue());
                    writeString(data, message.timestamp());
                    writeString(data, message.text());
                }
            }
            return sha256(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeString(DataOutputStream data, String value) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(value == null ? -1 : bytes.length);
        data.write(bytes);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String encodeMessageCursor(long chatId, int messageOffset, int characterOffset,
            String observedHash) {
        String plain = chatId + "|" + messageOffset + "|" + characterOffset + "|" + observedHash;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageCursor decodeMessageCursor(String cursor) {
        String[] fields = decodeCursor(cursor).split("\\|", -1);
        if (fields.length != 4) {
            throw new IllegalArgumentException("invalid message cursor");
        }
        try {
            return new MessageCursor(Long.parseLong(fields[0]), Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]), fields[3]);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid message cursor", failure);
        }
    }

    private static String encodeDocumentCursor(int offset, String hash) {
        String plain = offset + "|" + hash;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static DocumentCursor decodeDocumentCursor(String cursor) {
        String[] fields = decodeCursor(cursor).split("\\|", -1);
        if (fields.length != 2) {
            throw new IllegalArgumentException("invalid document cursor");
        }
        try {
            return new DocumentCursor(Integer.parseInt(fields[0]), fields[1]);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid document cursor", failure);
        }
    }

    private static String decodeCursor(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid cursor encoding", failure);
        }
    }

    public record SearchChatsResult(String outcome, List<ChatSearchHit> chats, int totalMatches,
            boolean capped, String searchBehavior) {
    }

    public record ChatSearchHit(long chatId, String title, String sourceType, String createdAt,
            String updatedAt, String importedAt, String snippet, String externalConversationId,
            String sourceUri, String transcriptHash) {
    }

    public record ChatMessagesResult(String outcome, long chatId, String title, String sourceType,
            String externalConversationId, String sourceUri, String storedTranscriptHash,
            String observedContentHash, int totalMessages, List<MessageChunk> messages,
            int returnedCharacters, String nextCursor, boolean truncated, String detail) {
        static ChatMessagesResult outcome(String outcome, long chatId, String detail) {
            return new ChatMessagesResult(outcome, chatId, null, null, null, null, null, null,
                    0, List.of(), 0, null, false, detail);
        }
    }

    public record MessageChunk(long messageId, int sequence, String role, String timestamp,
            String text, int characterStart, int characterEnd, int totalCharacters, boolean truncated) {
    }

    public record WorkingContextResult(String outcome, String relativePath, String observedHash,
            String text, int characterOffset, int totalCharacters, String nextCursor,
            boolean truncated, String detail, String evidenceLabel) {
    }

    private record MessageCursor(long chatId, int messageOffset, int characterOffset, String observedHash) {
    }

    private record DocumentCursor(int offset, String hash) {
    }

    private static final int defaultSearchLimit = 5;
    private static final int maximumSearchLimit = 10;
    private static final int defaultMessageLimit = 5;
    private static final int maximumMessageLimit = 10;
    private static final int defaultDocumentLimit = 4000;
    private static final int maximumDocumentLimit = 12000;
    private static final int messageTextBudget = 12000;
    private static final int queryLimit = 256;
    private static final int snippetLimit = 600;
    private static final String workingStateLabel =
            "Mutable project working state; evidence only, not governing authority or accepted truth.";

    private final SearchService searchService;
    private final ExportService exportService;
    private final ProjectDocumentReader documentReader;
    private final long projectId;
}
