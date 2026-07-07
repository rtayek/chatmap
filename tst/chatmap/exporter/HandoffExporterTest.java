package chatmap.exporter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.Source;
import chatmap.domain.Tag;
import chatmap.service.ExportService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.TagRepository;

class HandoffExporterTest {

    private static final String exportedAt = "2026-07-06T12:00:00Z";

    private Connection conn;
    private ProjectRepository projects;
    private ChatRepository chats;
    private MessageRepository messages;
    private TagRepository tags;
    private ExportService exportService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        projects = new ProjectRepository(conn);
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        tags = new TagRepository(conn);
        exportService = new ExportService(chats, messages, projects, tags);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void exportsProjectWithNoChats() throws Exception {
        Project project = projects.insert(new Project(0, "Empty Project", "No chats yet.",
                "2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z"));

        String markdown = exportProject(project.id());

        assertEquals(golden("project-empty.md"), markdown);
    }

    @Test
    void exportsOneChatWithTagsArchivedStatusAndMissingOptionalTimestamps() throws Exception {
        Project project = projects.insert(new Project(0, "One Chat Project", null,
                "2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z"));
        Chat chat = chats.insert(new Chat(0, project.id(), Source.markdown, "Planning Notes",
                null, null, "2026-07-03T08:00:00Z", true));
        Tag tag = tags.insert(new Tag(0, "planning"));
        tags.assignToChat(chat.id(), tag.id());
        messages.insert(new Message(0, chat.id(), "user",
                "Please turn these planning notes into a checklist.", 0, null, null));
        messages.insert(new Message(0, chat.id(), "unknown",
                "A note without a parsed role.", 1, null, null));

        String markdown = exportProject(project.id());

        assertEquals(golden("project-one-chat.md"), markdown);
    }

    @Test
    void exportsMultipleChatsInStableOrderWithAssistantPreview() throws Exception {
        Project project = projects.insert(new Project(0, "Knowledge Base", "Reusable chat notes.",
                "2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z"));
        Chat second = chats.insert(new Chat(0, project.id(), Source.plainText, "Later Chat",
                null, "2026-07-05T09:00:00Z", "2026-07-05T10:00:00Z", false));
        Chat first = chats.insert(new Chat(0, project.id(), Source.plainText, "Earlier Chat",
                "2026-07-04T09:00:00Z", null, "2026-07-04T10:00:00Z", false));
        Tag export = tags.insert(new Tag(0, "export"));
        Tag mvp = tags.insert(new Tag(0, "mvp"));
        tags.assignToChat(first.id(), mvp.id());
        tags.assignToChat(first.id(), export.id());

        messages.insert(new Message(0, first.id(), "assistant",
                "First draft before the user asks anything.", 0, null, null));
        messages.insert(new Message(0, first.id(), "user",
                "Summarize the storage and export work.", 1, null, null));
        messages.insert(new Message(0, first.id(), "assistant",
                "Storage is backed by SQLite. Export is deterministic Markdown.", 2, null, null));

        messages.insert(new Message(0, second.id(), "unknown",
                "No user or assistant roles were parsed here.", 0, null, null));

        String markdown = exportProject(project.id());

        assertEquals(golden("project-multiple-chats.md"), markdown);
    }

    private String exportProject(long projectId) throws Exception {
        ProjectHandoffModel model = exportService.loadProjectHandoff(projectId, exportedAt).orElseThrow();
        return new HandoffExporter().exportProject(model);
    }

    private static String golden(String name) throws Exception {
        return Files.readString(Path.of("tst", "chatmap", "exporter", "golden", name));
    }
}
