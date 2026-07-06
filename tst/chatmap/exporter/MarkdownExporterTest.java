package chatmap.exporter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.service.ExportService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

class MarkdownExporterTest {

    private Connection conn;

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void exportsHydratedChatAsDeterministicMarkdown() throws Exception {
        Chat chat = new Chat(7, null, "plain_text", "Sample Chat",
                "2026-07-05T10:00:00Z", null, "2026-07-06T00:00:00Z", false);
        List<Message> messages = List.of(
                new Message(11, 7, "unknown", "First line\nsecond line", 0, null, null),
                new Message(12, 7, "assistant", "Final answer.", 1, null, null));

        String markdown = new MarkdownExporter().exportChat(new ChatExportModel(chat, messages));

        assertEquals(golden("single-chat.md"), markdown);
    }

    @Test
    void exportServiceLoadsStoredChatAndMessagesInSequenceOrder() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        ChatRepository chats = new ChatRepository(conn);
        MessageRepository messages = new MessageRepository(conn);
        ExportService exportService = new ExportService(chats, messages);

        Chat storedChat = chats.insert(new Chat(0, null, "plain_text", "Stored Chat",
                null, null, "2026-07-06T00:00:00Z", false));
        Message second = messages.insert(new Message(0, storedChat.id(), "unknown",
                "Second message.", 1, null, null));
        Message first = messages.insert(new Message(0, storedChat.id(), "unknown",
                "First message.", 0, null, null));

        ChatExportModel loaded = exportService.loadChat(storedChat.id()).orElseThrow();
        String markdown = new MarkdownExporter().exportChat(loaded);

        assertEquals(List.of(first, second), loaded.messages());
        assertEquals(golden("stored-chat.md"), markdown);
    }

    private static String golden(String name) throws Exception {
        return Files.readString(Path.of("tst", "chatmap", "exporter", "golden", name));
    }
}
