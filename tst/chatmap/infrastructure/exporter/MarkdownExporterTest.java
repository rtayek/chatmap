package chatmap.infrastructure.exporter;

import chatmap.application.model.ChatExportModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;
import chatmap.application.service.ExportService;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.TagRepository;

class MarkdownExporterTest {

    private Connection conn;

    @TempDir
    private Path tempDir;

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void exportsHydratedChatAsDeterministicMarkdown() throws Exception {
        Chat chat = Chat.builder()
                            .id(7)
                            .projectId(null)
                            .source(Source.plainText)
                            .title("Sample Chat")
                            .createdAt("2026-07-05T10:00:00Z")
                            .updatedAt(null)
                            .importedAt("2026-07-06T00:00:00Z")
                            .archived(false)
                            .build();
        List<Message> messages = List.of(
                new Message(11, 7, MessageRole.unknown, "First line\nsecond line", 0, null, null),
                new Message(12, 7, MessageRole.assistant, "Final answer.", 1, null, null));

        String markdown = new MarkdownExporter().exportChat(new ChatExportModel(chat, messages));

        assertEquals(golden("single-chat.md"), markdown);
    }

    @Test
    void exportServiceLoadsStoredChatAndMessagesInSequenceOrder() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        ChatRepository chats = new ChatRepository(conn);
        MessageRepository messages = new MessageRepository(conn);
        ProjectRepository projects = new ProjectRepository(conn);
        TagRepository tags = new TagRepository(conn);
        ExportService exportService = new ExportService(chats, messages, projects, tags, new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter());

        Chat storedChat = chats.insert(Chat.builder()
                                               .id(0)
                                               .projectId(null)
                                               .source(Source.plainText)
                                               .title("Stored Chat")
                                               .createdAt(null)
                                               .updatedAt(null)
                                               .importedAt("2026-07-06T00:00:00Z")
                                               .archived(false)
                                               .build());
        Message second = messages.insert(new Message(0, storedChat.id(), MessageRole.unknown,
                "Second message.", 1, null, null));
        Message first = messages.insert(new Message(0, storedChat.id(), MessageRole.unknown,
                "First message.", 0, null, null));

        ChatExportModel loaded = exportService.loadChat(storedChat.id()).orElseThrow();
        String markdown = new MarkdownExporter().exportChat(loaded);

        assertEquals(List.of(first, second), loaded.messages());
        assertEquals(golden("stored-chat.md"), markdown);
    }

    @Test
    void exportServiceWritesSelectedChatMarkdownFile() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        ChatRepository chats = new ChatRepository(conn);
        MessageRepository messages = new MessageRepository(conn);
        ProjectRepository projects = new ProjectRepository(conn);
        TagRepository tags = new TagRepository(conn);
        ExportService exportService = new ExportService(chats, messages, projects, tags, new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter());

        Chat storedChat = chats.insert(Chat.builder()
                                               .id(0)
                                               .projectId(null)
                                               .source(Source.markdown)
                                               .title("UI Export Chat")
                                               .createdAt(null)
                                               .updatedAt(null)
                                               .importedAt("2026-07-06T00:00:00Z")
                                               .archived(false)
                                               .build());
        messages.insert(new Message(0, storedChat.id(), MessageRole.unknown,
                "Export this selected chat.", 0, null, null));
        Path outputPath = tempDir.resolve("selected-chat.md");

        assertTrue(exportService.writeChatMarkdown(storedChat.id(), outputPath));
        String markdown = Files.readString(outputPath);
        assertTrue(markdown.contains("# UI Export Chat"));
        assertTrue(markdown.contains("Source: markdown"));
        assertTrue(markdown.contains("Export this selected chat."));
    }

    private static String golden(String name) throws Exception {
        return Files.readString(Path.of("tst", "chatmap", "infrastructure", "exporter", "golden", name));
    }
}
