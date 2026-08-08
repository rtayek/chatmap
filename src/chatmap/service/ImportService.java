package chatmap.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.importer.ChatGptJsonImporter;
import chatmap.importer.ImportedChat;
import chatmap.importer.MarkdownImporter;
import chatmap.importer.PlainTextImporter;
import chatmap.storage.ChatRepository;
import chatmap.storage.MessageRepository;
import chatmap.storage.TransactionRunner;

/** Imports selected files through format-specific importers, then persists normalized data. */
public final class ImportService {

    public enum Outcome {
        inserted,
        updated,
        unchanged
    }

    public record PersistResult(Chat chat, Outcome outcome) {
    }

    private final ChatRepository chats;
    private final MessageRepository messages;
    private final TransactionRunner transactions;

    public ImportService(ChatRepository chats, MessageRepository messages) {
        this(chats, messages, new TransactionRunner(chats.connection()));
    }

    public ImportService(ChatRepository chats, MessageRepository messages, TransactionRunner transactions) {
        if (chats.connection() != messages.connection()) {
            throw new IllegalArgumentException("Import repositories must share one transaction connection.");
        }
        this.chats = chats;
        this.messages = messages;
        this.transactions = transactions;
    }

    public Chat importFile(Path file) throws IOException, SQLException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String importedAt = Instant.now().toString();
        String baseUri = file.toAbsolutePath().normalize().toUri().toString();
        List<ImportedChat> imported = switch (extension(file)) {
            case "md", "markdown" -> List.of(new MarkdownImporter().importMarkdown(text, fileName(file), importedAt));
            // A ChatGPT conversations.json is an array of every chat; import them all
            // rather than silently keeping only the first.
            case "json" -> new ChatGptJsonImporter().importAll(text, importedAt);
            default -> List.of(new PlainTextImporter().importText(fileName(file), text, importedAt));
        };
        if (imported.isEmpty()) {
            throw new IOException("No importable conversation found in " + fileName(file));
        }

        Chat firstStored = null;
        for (int index = 0; index < imported.size(); index++) {
            String sourceUri = imported.size() == 1 ? baseUri : baseUri + "#conversation=" + (index + 1);
            Chat stored = persist(withSourceUri(imported.get(index), sourceUri)).chat();
            if (firstStored == null) {
                firstStored = stored;
            }
        }
        return firstStored;
    }

    public PersistResult persist(ImportedChat imported) throws SQLException {
        return transactions.inTransaction(() -> persistInTransaction(imported));
    }

    private PersistResult persistInTransaction(ImportedChat imported) throws SQLException {
        String transcriptHash = ChatContentHasher.hash(imported.messages());
        Chat incoming = withComputedImportMetadata(imported.chat(), transcriptHash);

        if (incoming.externalConversationId() != null) {
            return persistWithExternalIdentity(incoming, imported.messages());
        }

        if (incoming.source().isProvider()) {
            var exactDuplicate = chats.findBySourceAndContentHash(incoming.source(), transcriptHash);
            if (exactDuplicate.isPresent()) {
                Chat existing = exactDuplicate.get();
                Chat updated = chats.updateImportMetadata(existing.id(), incoming.title(), incoming.sourceUri(),
                        transcriptHash, incoming.sourceUpdatedAt(), incoming.lastImportedAt());
                return new PersistResult(updated, Outcome.unchanged);
            }
        }

        Chat storedChat = chats.insert(incoming);
        insertMessages(storedChat.id(), imported.messages());
        return new PersistResult(storedChat, Outcome.inserted);
    }

    private PersistResult persistWithExternalIdentity(Chat incoming, List<Message> incomingMessages)
            throws SQLException {
        var existing = chats.findByExternalIdentity(incoming.source(), incoming.externalConversationId());
        if (existing.isEmpty()) {
            Chat storedChat = chats.insert(incoming);
            insertMessages(storedChat.id(), incomingMessages);
            return new PersistResult(storedChat, Outcome.inserted);
        }

        Chat stored = existing.get();
        if (incoming.contentHash().equals(stored.contentHash())) {
            Chat updated = chats.updateImportMetadata(stored.id(), incoming.title(), incoming.sourceUri(),
                    incoming.contentHash(), incoming.sourceUpdatedAt(), incoming.lastImportedAt());
            return new PersistResult(updated, Outcome.unchanged);
        }

        chats.updateFromSource(stored.id(), incoming.source(), incoming.title(), incoming.createdAt(), incoming.updatedAt(),
                incoming.externalConversationId(), incoming.sourceUri(), incoming.contentHash(),
                incoming.sourceUpdatedAt(), incoming.lastImportedAt());
        messages.deleteByChat(stored.id());
        insertMessages(stored.id(), incomingMessages);
        return new PersistResult(chats.findById(stored.id()).orElseThrow(), Outcome.updated);
    }

    private void insertMessages(long chatId, List<Message> incomingMessages) throws SQLException {
        if (incomingMessages.isEmpty()) {
            return;
        }
        List<Message> prepared = incomingMessages.stream()
                .map(message -> new Message(0, chatId, message.role(), message.text(),
                        message.sequence(), message.timestamp(), message.rawJson()))
                .toList();
        messages.insertAll(prepared);
    }

    private static ImportedChat withSourceUri(ImportedChat imported, String sourceUri) {
        Chat withUri = imported.chat().toBuilder().sourceUri(sourceUri).build();
        return new ImportedChat(withUri, imported.messages());
    }

    private static Chat withComputedImportMetadata(Chat chat, String contentHash) {
        String lastImportedAt = chat.lastImportedAt() == null ? chat.importedAt() : chat.lastImportedAt();
        String sourceUpdatedAt = chat.sourceUpdatedAt() == null ? chat.updatedAt() : chat.sourceUpdatedAt();
        return chat.toBuilder()
                .externalConversationId(blankToNull(chat.externalConversationId()))
                .sourceUri(blankToNull(chat.sourceUri()))
                .contentHash(contentHash)
                .sourceUpdatedAt(sourceUpdatedAt)
                .lastImportedAt(lastImportedAt)
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String extension(Path file) {
        String name = fileName(file).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    private static String fileName(Path file) {
        Path name = file.getFileName();
        return name == null ? "Imported Chat" : name.toString();
    }
}
