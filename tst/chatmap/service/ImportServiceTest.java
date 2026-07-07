package chatmap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

class ImportServiceTest {

    @TempDir
    Path tempDir;

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ImportService importService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        importService = new ImportService(chats, messages);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void importsPlainTextFile() throws Exception {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Plain text import body");

        Chat chat = importService.importFile(file);

        assertEquals("notes.txt", chat.title());
        assertEquals(Source.plainText, chat.source());
        assertEquals(List.of("Plain text import body"), messages.findByChat(chat.id()).stream().map(Message::text).toList());
    }

    @Test
    void importsMarkdownFile() throws Exception {
        Path file = tempDir.resolve("notes.md");
        Files.writeString(file, "# Markdown Title\n\nMarkdown body");

        Chat chat = importService.importFile(file);

        assertEquals("Markdown Title", chat.title());
        assertEquals(Source.markdown, chat.source());
        assertEquals(List.of(chat), chats.findAll());
    }
}
