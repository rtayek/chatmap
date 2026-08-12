package chatmap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Project;
import chatmap.domain.Source;
import chatmap.domain.Tag;
import chatmap.exporter.ChatExportModel;
import chatmap.exporter.ProjectHandoffModel;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.TagRepository;

class ExportServiceTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ProjectRepository projects;
    private TagRepository tags;
    private ExportService exportService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        projects = new ProjectRepository(conn);
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
    void loadChatHydratesChatAndItsMessages() throws Exception {
        Chat chat = chats.insert(new Chat(0, null, Source.plainText, "Hydrated",
                null, null, "2026-08-10T00:00:00Z", false));
        Message first = messages.insert(new Message(0, chat.id(), MessageRole.user, "hi", 0, null, null));
        Message second = messages.insert(new Message(0, chat.id(), MessageRole.assistant, "hello", 1, null, null));

        ChatExportModel model = exportService.loadChat(chat.id()).orElseThrow();

        assertEquals(chat, model.chat());
        assertEquals(List.of(first, second), model.messages());
        assertTrue(exportService.loadChat(999).isEmpty());
    }

    @Test
    void loadProjectHandoffHydratesChatsMessagesAndTags() throws Exception {
        Project project = projects.insert(new Project(0, "Handoff Project", null,
                "2026-08-10T00:00:00Z", "2026-08-10T00:00:00Z"));
        Chat chat = chats.insert(new Chat(0, project.id(), Source.plainText, "In project",
                null, null, "2026-08-10T00:00:00Z", false));
        Message message = messages.insert(new Message(0, chat.id(), MessageRole.user, "hi", 0, null, null));
        Tag tag = tags.insert(new Tag(0, "MVP"));
        tags.assignToChat(chat.id(), tag.id());

        ProjectHandoffModel model = exportService.loadProjectHandoff(
                project.id(), "2026-08-10T01:00:00Z").orElseThrow();

        assertEquals(project, model.project());
        assertEquals(1, model.chats().size());
        ProjectHandoffModel.ChatEntry entry = model.chats().get(0);
        assertEquals(chat, entry.chat());
        assertEquals(List.of(message), entry.messages());
        assertEquals(List.of(tag), entry.tags());
        assertTrue(exportService.loadProjectHandoff(999, "2026-08-10T01:00:00Z").isEmpty());
    }

    @Test
    void loadProjectHandoffIsAtomicAgainstAConcurrentWriter() throws Exception {
        Project project = projects.insert(new Project(0, "Concurrent Project", null,
                "2026-08-10T00:00:00Z", "2026-08-10T00:00:00Z"));
        chats.insert(new Chat(0, project.id(), Source.plainText, "One",
                null, null, "2026-08-10T00:00:00Z", false));

        // Hold the shared connection's monitor from outside, the same way a
        // writer holding it mid-transaction would. If loadProjectHandoff's
        // reads were not wrapped in one transaction, some of its individual
        // synchronized(conn) calls would still be able to interleave; with
        // the fix, the whole read is one critical section and must wait.
        CountDownLatch started = new CountDownLatch(1);
        boolean[] completedWhileLockHeld = {false};

        synchronized (conn) {
            CompletableFuture<ProjectHandoffModel> future = CompletableFuture.supplyAsync(() -> {
                started.countDown();
                try {
                    return exportService.loadProjectHandoff(project.id(), "2026-08-10T01:00:00Z")
                            .orElseThrow();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

            assertTrue(started.await(2, TimeUnit.SECONDS));
            // Give the background thread every chance to have raced ahead if it could.
            try {
                future.get(200, TimeUnit.MILLISECONDS);
                completedWhileLockHeld[0] = true;
            } catch (java.util.concurrent.TimeoutException expected) {
                completedWhileLockHeld[0] = false;
            }
        }

        assertFalse(completedWhileLockHeld[0],
                "loadProjectHandoff must not complete while the connection lock is held elsewhere");
    }
}
