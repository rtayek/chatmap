package chatmap.application.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.TagRepository;

class SampleRoundTripTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ImportService importService;
    private ExportService exportService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());
        exportService = new ExportService(chats, messages, new ProjectRepository(conn), new TagRepository(conn), new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void plainTextSampleImportsSearchesAndExports() throws Exception {
        Chat chat = importService.importFile(sample("plainTextSample.txt"));

        assertSearchFindsChat("organize", chat);

        String markdown = exportService.exportChatMarkdown(chat.id()).orElseThrow();
        assertTrue(markdown.contains("# plainTextSample.txt"));
        assertTrue(markdown.contains("Source: plainText"));
        assertTrue(markdown.contains("## user"));
        assertTrue(markdown.contains("How can I use ChatMap to organize imported chats?"));
        assertTrue(markdown.contains("## assistant"));
        assertTrue(markdown.contains("Import a chat file, search for ChatMap"));
        assertFalse(markdown.contains("## unknown"));
    }

    @Test
    void markdownSampleImportsSearchesAndExports() throws Exception {
        Chat chat = importService.importFile(sample("markdownSample.md"));

        assertSearchFindsChat("heading", chat);

        String markdown = exportService.exportChatMarkdown(chat.id()).orElseThrow();
        assertTrue(markdown.contains("# ChatMap Markdown Sample"));
        assertTrue(markdown.contains("Source: markdown"));
        assertTrue(markdown.contains("## user"));
        assertTrue(markdown.contains("Can ChatMap import Markdown notes?"));
        assertTrue(markdown.contains("## assistant"));
        assertTrue(markdown.contains("Yes. The Markdown importer preserves"));
        assertFalse(markdown.contains("## unknown"));
    }

    @Test
    void chatGptSampleImportsSearchesAndExports() throws Exception {
        Chat chat = importService.importFile(sample("chatGptSample.json"));

        assertSearchFindsChat("FTS5", chat);

        String markdown = exportService.exportChatMarkdown(chat.id()).orElseThrow();
        assertTrue(markdown.contains("# ChatMap ChatGPT Sample"));
        assertTrue(markdown.contains("Source: chatgptJson"));
        assertTrue(markdown.contains("## user"));
        assertTrue(markdown.contains("How can ChatMap help me search imported conversations?"));
        assertTrue(markdown.contains("## assistant"));
        assertTrue(markdown.contains("ChatMap stores messages in SQLite and uses FTS5"));
    }

    private void assertSearchFindsChat(String query, Chat chat) throws Exception {
        List<Long> hits = messages.searchText(query);
        assertEquals(1, hits.size());
        assertEquals(chat.id(), messages.findByChat(chat.id()).stream()
                .filter(message -> message.id() == hits.getFirst())
                .findFirst()
                .orElseThrow()
                .chatId());
    }

    private static Path sample(String fileName) {
        return Path.of("samples", fileName);
    }
}
