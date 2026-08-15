package chatmap.presentation.ui;

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

import chatmap.infrastructure.ai.StandardCliBackend;
import chatmap.domain.Project;
import chatmap.domain.Tag;
import chatmap.application.service.ExportService;
import chatmap.application.service.ImportService;
import chatmap.application.service.LiveChatFetchService;
import chatmap.application.service.ProjectService;
import chatmap.application.service.SearchService;
import chatmap.application.service.SummaryService;
import chatmap.application.service.TagService;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.SearchRepository;
import chatmap.infrastructure.persistence.sqlite.SummaryRepository;
import chatmap.infrastructure.persistence.sqlite.TagRepository;

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
        ImportService importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());
        SummaryService summaryService = new SummaryService(chats, messages,
                new SummaryRepository(conn), tags,
                StandardCliBackend.claude(java.time.Duration.ofMinutes(3)));
        LiveChatFetchService liveChatFetchService =
                new LiveChatFetchService(List.of(), importService, chats);
        controller = new ChatMapController(
                importService,
                new ExportService(chats, messages, projects, tags, new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter()),
                new SearchService(new SearchRepository(conn)),
                new ProjectService(projects, chats),
                new TagService(tags, chats),
                summaryService,
                liveChatFetchService);
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
        assertEquals(List.of(markdownChatId, plainTextChatId), chatIds(controller.searchChats("ChatMap")));

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
