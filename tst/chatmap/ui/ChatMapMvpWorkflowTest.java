package chatmap.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Project;
import chatmap.domain.Tag;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.service.ProjectService;
import chatmap.service.SearchService;
import chatmap.service.TagService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.SearchRepository;
import chatmap.storage.TagRepository;

class ChatMapMvpWorkflowTest {

    @TempDir
    Path tempDir;

    private Connection conn;
    private ChatMapController controller;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        ChatRepository chats = new ChatRepository(conn);
        MessageRepository messages = new MessageRepository(conn);
        ProjectRepository projects = new ProjectRepository(conn);
        TagRepository tags = new TagRepository(conn);
        controller = new ChatMapController(
                new ImportService(chats, messages),
                new ExportService(chats, messages, projects, tags),
                new SearchService(new SearchRepository(conn)),
                new ProjectService(projects, chats),
                new TagService(tags, chats));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void completesImportSearchOrganizeAndExportWorkflow() throws Exception {
        long plainTextChatId = controller.importFile(sample("plainTextSample.txt")).selectedChatId();
        long markdownChatId = controller.importFile(sample("markdownSample.md")).selectedChatId();

        ChatListState.Snapshot allChats = controller.loadAllChats();
        assertEquals(List.of(plainTextChatId, markdownChatId), chatIds(allChats));

        Project project = controller.createProject("Smoke Project");
        controller.assignProject(plainTextChatId, project.id());
        assertEquals(List.of(plainTextChatId), chatIds(controller.filterByProject(project.id())));
        assertEquals(List.of(plainTextChatId, markdownChatId), chatIds(controller.clearFilters()));

        Tag tag = controller.createTag("Smoke");
        controller.addTag(markdownChatId, tag.id());
        assertEquals(List.of(markdownChatId), chatIds(controller.filterByTag(tag.id())));
        assertTrue(controller.removeTag(markdownChatId, tag.id()).currentItems().isEmpty());

        controller.clearFilters();
        assertEquals(List.of(plainTextChatId, markdownChatId), chatIds(controller.searchChats("ChatMap")));

        controller.selectChat(markdownChatId);
        Path output = tempDir.resolve("markdown-sample-export.md");
        assertTrue(controller.exportChatMarkdown(markdownChatId, output));
        assertTrue(Files.exists(output));
        String markdown = Files.readString(output);
        assertTrue(markdown.contains("# ChatMap Markdown Sample"));
        assertTrue(markdown.contains("Can ChatMap import Markdown notes?"));
    }

    private static Path sample(String name) {
        return Path.of("samples", name);
    }

    private static List<Long> chatIds(ChatListState.Snapshot snapshot) {
        return snapshot.currentItems().stream()
                .map(result -> result.chatId())
                .toList();
    }
}
