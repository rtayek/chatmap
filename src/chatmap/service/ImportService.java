package chatmap.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
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
        return persist(imported);
    }

    public Chat persist(ImportedChat imported) throws SQLException {
        Chat storedChat = chats.insert(imported.chat());
        for (Message message : imported.messages()) {
            messages.insert(new Message(0, storedChat.id(), message.role(), message.text(),
                    message.sequence(), message.timestamp(), message.rawJson()));
        }
        return storedChat;
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
