package chatmap.infrastructure.exporter;

import chatmap.application.model.ProjectHandoffModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Project;
import chatmap.domain.Source;
import chatmap.domain.Tag;
import chatmap.application.service.ExportService;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.TagRepository;

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
        exportService = new ExportService(chats, messages, projects, tags, new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter());
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
        messages.insert(new Message(0, chat.id(), MessageRole.user,
                "Please turn these planning notes into a checklist.", 0, null, null));
        messages.insert(new Message(0, chat.id(), MessageRole.unknown,
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

        messages.insert(new Message(0, first.id(), MessageRole.assistant,
                "First draft before the user asks anything.", 0, null, null));
        messages.insert(new Message(0, first.id(), MessageRole.user,
                "Summarize the storage and export work.", 1, null, null));
        messages.insert(new Message(0, first.id(), MessageRole.assistant,
                "Storage is backed by SQLite. Export is deterministic Markdown.", 2, null, null));

        messages.insert(new Message(0, second.id(), MessageRole.unknown,
                "No user or assistant roles were parsed here.", 0, null, null));

        String markdown = exportProject(project.id());

        assertEquals(golden("project-multiple-chats.md"), markdown);
    }

    @Test
    void loadProjectHandoffHydratesMessagesAndTagsForAllProjectChats() throws Exception {
        Project project = projects.insert(new Project(0, "Hydrated", null,
                "2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z"));
        Chat first = chats.insert(new Chat(0, project.id(), Source.plainText, "First",
                null, null, "2026-07-03T00:00:00Z", false));
        Chat second = chats.insert(new Chat(0, project.id(), Source.plainText, "Second",
                null, null, "2026-07-04T00:00:00Z", false));
        Chat empty = chats.insert(new Chat(0, project.id(), Source.plainText, "Empty",
                null, null, "2026-07-05T00:00:00Z", false));
        Tag alpha = tags.insert(new Tag(0, "Alpha"));
        Tag zebra = tags.insert(new Tag(0, "zebra"));
        tags.assignToChat(first.id(), zebra.id());
        tags.assignToChat(first.id(), alpha.id());
        Message later = messages.insert(new Message(0, first.id(), MessageRole.assistant, "later", 1, null, null));
        Message earlier = messages.insert(new Message(0, first.id(), MessageRole.user, "earlier", 0, null, null));
        Message only = messages.insert(new Message(0, second.id(), MessageRole.user, "only", 0, null, null));

        ProjectHandoffModel model = exportService.loadProjectHandoff(project.id(), exportedAt).orElseThrow();

        assertEquals(List.of(first.id(), second.id(), empty.id()),
                model.chats().stream().map(entry -> entry.chat().id()).toList());
        assertEquals(List.of(earlier, later), model.chats().get(0).messages());
        assertEquals(List.of(alpha, zebra), model.chats().get(0).tags());
        assertEquals(List.of(only), model.chats().get(1).messages());
        assertEquals(List.of(), model.chats().get(1).tags());
        assertEquals(List.of(), model.chats().get(2).messages());
        assertEquals(List.of(), model.chats().get(2).tags());
    }

    private String exportProject(long projectId) throws Exception {
        ProjectHandoffModel model = exportService.loadProjectHandoff(projectId, exportedAt).orElseThrow();
        return new HandoffExporter().exportProject(model);
    }

    private static String golden(String name) throws Exception {
        return Files.readString(Path.of("tst", "chatmap", "exporter", "golden", name));
    }
}
