package chatmap.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Connection;
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

/** Imports selected files through format-specific importers, then persists normalized data. */
public final class ImportService {

    private final ChatRepository chats;
    private final MessageRepository messages;

    public ImportService(ChatRepository chats, MessageRepository messages) {
        this.chats = chats;
        this.messages = messages;
    }

    public Chat importFile(Path file) throws IOException, SQLException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String importedAt = Instant.now().toString();
        ImportedChat imported = switch (extension(file)) {
            case "md", "markdown" -> new MarkdownImporter().importMarkdown(text, fileName(file), importedAt);
            case "json" -> new ChatGptJsonImporter().importJson(text, importedAt);
            default -> new PlainTextImporter().importText(fileName(file), text, importedAt);
        };
        return persist(withSourceUri(imported, file.toAbsolutePath().normalize().toUri().toString()));
    }

    public Chat persist(ImportedChat imported) throws SQLException {
        Connection conn = chats.connection();
        boolean autoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            Chat stored = persistInTransaction(imported);
            conn.commit();
            return stored;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private Chat persistInTransaction(ImportedChat imported) throws SQLException {
        String contentHash = ChatContentHasher.hash(imported.messages());
        Chat incoming = withComputedImportMetadata(imported.chat(), contentHash);

        if (incoming.externalConversationId() != null) {
            return persistWithExternalIdentity(incoming, imported.messages());
        }

        if (isProviderSource(incoming.source())) {
            var exactDuplicate = chats.findBySourceAndContentHash(incoming.source(), contentHash);
            if (exactDuplicate.isPresent()) {
                Chat existing = exactDuplicate.get();
                return chats.updateImportMetadata(existing.id(), incoming.sourceUri(), contentHash,
                        incoming.sourceUpdatedAt(), incoming.lastImportedAt());
            }
        }

        Chat storedChat = chats.insert(incoming);
        insertMessages(storedChat.id(), imported.messages());
        return storedChat;
    }

    private Chat persistWithExternalIdentity(Chat incoming, List<Message> incomingMessages)
            throws SQLException {
        var existing = chats.findByExternalIdentity(incoming.source(), incoming.externalConversationId());
        if (existing.isEmpty()) {
            Chat storedChat = chats.insert(incoming);
            insertMessages(storedChat.id(), incomingMessages);
            return storedChat;
        }

        Chat stored = existing.get();
        if (incoming.contentHash().equals(stored.contentHash())) {
            return chats.updateImportMetadata(stored.id(), incoming.sourceUri(), incoming.contentHash(),
                    incoming.sourceUpdatedAt(), incoming.lastImportedAt());
        }

        chats.updateFromSource(stored.id(), incoming.source(), incoming.createdAt(), incoming.updatedAt(),
                incoming.externalConversationId(), incoming.sourceUri(), incoming.contentHash(),
                incoming.sourceUpdatedAt(), incoming.lastImportedAt());
        messages.deleteByChat(stored.id());
        insertMessages(stored.id(), incomingMessages);
        return chats.findById(stored.id()).orElseThrow();
    }

    private void insertMessages(long chatId, List<Message> incomingMessages) throws SQLException {
        for (Message message : incomingMessages) {
            messages.insert(new Message(0, chatId, message.role(), message.text(),
                    message.sequence(), message.timestamp(), message.rawJson()));
        }
    }

    private static ImportedChat withSourceUri(ImportedChat imported, String sourceUri) {
        Chat chat = imported.chat();
        Chat withUri = new Chat(chat.id(), chat.projectId(), chat.source(), chat.title(),
                chat.createdAt(), chat.updatedAt(), chat.importedAt(), chat.archived(),
                chat.externalConversationId(), sourceUri, chat.contentHash(),
                chat.sourceUpdatedAt(), chat.lastImportedAt());
        return new ImportedChat(withUri, imported.messages());
    }

    private static Chat withComputedImportMetadata(Chat chat, String contentHash) {
        String lastImportedAt = chat.lastImportedAt() == null ? chat.importedAt() : chat.lastImportedAt();
        String sourceUpdatedAt = chat.sourceUpdatedAt() == null ? chat.updatedAt() : chat.sourceUpdatedAt();
        return new Chat(chat.id(), chat.projectId(), chat.source(), chat.title(),
                chat.createdAt(), chat.updatedAt(), chat.importedAt(), chat.archived(),
                blankToNull(chat.externalConversationId()), blankToNull(chat.sourceUri()),
                contentHash, sourceUpdatedAt, lastImportedAt);
    }

    private static boolean isProviderSource(chatmap.domain.Source source) {
        return switch (source) {
            case claudeWeb, chatGptWeb, geminiWeb, claudeCode, codexCli, geminiCli -> true;
            default -> false;
        };
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
