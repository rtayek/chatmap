package chatmap.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

import chatmap.backend.ClaudeCliClient;
import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.domain.Message;
import chatmap.domain.Tag;
import chatmap.storage.ChatRepository;
import chatmap.storage.MessageRepository;
import chatmap.storage.SummaryRepository;
import chatmap.storage.TagRepository;

/**
 * Generates an AI summary and tags for an already-imported chat.
 *
 * This is a deliberate, on-demand extra step, not part of import. It only
 * ever adds a new chatSummaries row and assigns existing/new tags; it never
 * edits chats or messages. Calling it twice on the same chat produces two
 * summary rows, both kept (see SummaryRepository.findAllForChat).
 *
 * For now this always summarizes with the same backend that produced the
 * chat (currently: Claude only). Using a different backend to review is a
 * possible later step, deliberately not built yet.
 */
public final class SummaryService {

    private static final String GENERATED_BY = "claude";

    private final ChatRepository chats;
    private final MessageRepository messages;
    private final SummaryRepository summaries;
    private final TagRepository tags;
    private final ClaudeCliClient claude;

    public SummaryService(ChatRepository chats, MessageRepository messages, SummaryRepository summaries,
            TagRepository tags, ClaudeCliClient claude) {
        this.chats = chats;
        this.messages = messages;
        this.summaries = summaries;
        this.tags = tags;
        this.claude = claude;
    }

    /** The most recent summary for a chat, if it has been summarized. Read-only. */
    public Optional<ChatSummary> latestSummary(long chatId) throws SQLException {
        return summaries.findLatestForChat(chatId);
    }

    /** Summarizes and tags the given chat, saving both. Returns the stored summary. */
    public ChatSummary summarize(long chatId) throws SQLException, IOException {
        Chat chat = chats.findById(chatId)
                .orElseThrow(() -> new NoSuchElementException("No chat with id " + chatId));
        List<Message> chatMessages = messages.findByChat(chatId);

        String prompt = buildPrompt(chat, chatMessages);
        String response = claude.ask(prompt);

        Parsed parsed = parse(response);
        String contentHash = chat.contentHash() == null
                ? ChatContentHasher.hash(chatMessages)
                : chat.contentHash();

        Connection conn = summaries.connection();
        boolean autoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            if (chat.contentHash() == null) {
                chats.updateImportMetadata(chatId, chat.sourceUri(), contentHash,
                        chat.sourceUpdatedAt(), chat.lastImportedAt());
            }
            ChatSummary stored = summaries.insert(
                    new ChatSummary(0, chatId, parsed.summaryText(), GENERATED_BY,
                            Instant.now().toString(), contentHash));

            for (String tagName : parsed.tagNames()) {
                Tag tag = getOrCreateTag(tagName);
                tags.assignToChat(chatId, tag.id());
            }

            conn.commit();
            return stored;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private Tag getOrCreateTag(String tagName) throws SQLException {
        Optional<Tag> existing = tags.findByName(tagName);
        if (existing.isPresent()) {
            return existing.get();
        }
        return tags.insert(new Tag(0, tagName));
    }

    /** Builds the prompt sent to the backend. Package-visible for tests. */
    static String buildPrompt(Chat chat, List<Message> chatMessages) {
        StringBuilder transcript = new StringBuilder();
        for (Message m : chatMessages) {
            transcript.append(m.role().toUpperCase(Locale.ROOT)).append(": ").append(m.text()).append("\n\n");
        }

        return """
                Read the conversation below and respond with exactly two lines, nothing else.

                Line 1 starts with "SUMMARY: " followed by a two or three sentence plain-language \
                summary of what was discussed and decided.
                Line 2 starts with "TAGS: " followed by three to six short lowercase tags, \
                comma-separated, no punctuation.

                Conversation title: %s

                Conversation:
                ---
                %s---
                """.formatted(chat.title(), transcript);
    }

    /** Parses the fixed SUMMARY:/TAGS: response format. Package-visible for tests. */
    static Parsed parse(String response) {
        String summaryText = "";
        List<String> tagNames = new ArrayList<>();

        for (String line : response.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, "SUMMARY:", 0, 8)) {
                summaryText = trimmed.substring(8).trim();
            } else if (trimmed.regionMatches(true, 0, "TAGS:", 0, 5)) {
                String rest = trimmed.substring(5).trim();
                for (String tag : rest.split(",")) {
                    String cleaned = tag.trim().toLowerCase(Locale.ROOT);
                    if (!cleaned.isEmpty()) {
                        tagNames.add(cleaned);
                    }
                }
            }
        }

        if (summaryText.isEmpty()) {
            // Backend did not follow the format; fall back to the whole response
            // rather than losing it, so nothing silently disappears.
            summaryText = response.trim();
        }

        return new Parsed(summaryText, tagNames);
    }

    record Parsed(String summaryText, List<String> tagNames) {
    }
}
