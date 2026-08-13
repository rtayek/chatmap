package chatmap.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import chatmap.application.port.persistence.ChatStore;
import chatmap.application.port.persistence.MessageStore;
import chatmap.application.port.persistence.TransactionManager;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.importer.ChatGptJsonImporter;
import chatmap.importer.ImportedChat;
import chatmap.importer.MarkdownImporter;
import chatmap.importer.PlainTextImporter;

/** Imports selected files through format-specific importers, then persists normalized data. */
public final class ImportService {

    public enum Outcome {
        inserted,
        updated,
        unchanged
    }

    public record PersistResult(Chat chat, Outcome outcome) {
    }

    private final ChatStore chats;
    private final MessageStore messages;
    private final TransactionManager transactions;

    public ImportService(ChatStore chats, MessageStore messages) {
        this(chats, messages, chats.transactions());
    }

    public ImportService(ChatStore chats, MessageStore messages, TransactionManager transactions) {
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

        // No external identity to dedup by (true for every plainText/markdown import, and
        // for any provider fetch that didn't supply one) — content hash is the only
        // remaining way to recognize "this is the same conversation again."
        var exactDuplicate = chats.findBySourceAndContentHash(incoming.source(), transcriptHash);
        if (exactDuplicate.isPresent()) {
            return unchangedResult(exactDuplicate.get(), incoming, transcriptHash);
        }

        try {
            Chat storedChat = chats.insert(incoming);
            insertMessages(storedChat.id(), imported.messages());
            return new PersistResult(storedChat, Outcome.inserted);
        } catch (SQLException e) {
            if (!chats.isUniqueConstraintError(e)) {
                throw e;
            }
            // chatsContentHashIndex backstop: another writer (e.g. ChatConsolidatorCli
            // running alongside the GUI, a separate process with its own serialized
            // executor) inserted the same (source, contentHash) between our check above
            // and this insert. Treat it the same as if we'd found it there.
            Chat winner = chats.findBySourceAndContentHash(incoming.source(), transcriptHash).orElseThrow(() -> e);
            return unchangedResult(winner, incoming, transcriptHash);
        }
    }

    private PersistResult unchangedResult(Chat existing, Chat incoming, String transcriptHash) throws SQLException {
        Chat updated = chats.updateImportMetadata(existing.id(), incoming.title(), incoming.sourceUri(),
                transcriptHash, incoming.sourceUpdatedAt(), incoming.lastImportedAt());
        return new PersistResult(updated, Outcome.unchanged);
    }

    private PersistResult persistWithExternalIdentity(Chat incoming, List<Message> incomingMessages)
            throws SQLException {
        var existing = chats.findByExternalIdentity(incoming.source(), incoming.externalConversationId());
        if (existing.isEmpty()) {
            try {
                Chat storedChat = chats.insert(incoming);
                insertMessages(storedChat.id(), incomingMessages);
                return new PersistResult(storedChat, Outcome.inserted);
            } catch (SQLException e) {
                if (!chats.isUniqueConstraintError(e)) {
                    throw e;
                }
                // chatsExternalIdentityIndex backstop: another writer won this identity
                // between our check above and this insert. Fall back to it, same as the
                // "already exists" branch below.
                existing = Optional.of(chats.findByExternalIdentity(incoming.source(), incoming.externalConversationId())
                        .orElseThrow(() -> e));
            }
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
