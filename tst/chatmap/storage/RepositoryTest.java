package chatmap.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.Source;
import chatmap.domain.Tag;

class RepositoryTest {

    private Connection conn;
    private ProjectRepository projects;
    private ChatRepository chats;
    private MessageRepository messages;
    private TagRepository tags;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        projects = new ProjectRepository(conn);
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        tags = new TagRepository(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void createsUpdatesAndDeletesProject() throws Exception {
        Project created = projects.insert(new Project(0, "Work", "Initial",
                "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));

        assertEquals("Work", projects.findById(created.id()).orElseThrow().name());

        projects.update(new Project(created.id(), "Personal", "Updated",
                created.createdAt(), "2026-07-06T01:00:00Z"));

        Project updated = projects.findById(created.id()).orElseThrow();
        assertEquals("Personal", updated.name());
        assertEquals("Updated", updated.description());

        projects.delete(created.id());

        assertTrue(projects.findById(created.id()).isEmpty());
    }

    @Test
    void createsUpdatesArchivesAndDeletesChat() throws Exception {
        Project project = projects.insert(new Project(0, "Project", null,
                "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));
        Chat chat = chats.insert(new Chat(0, project.id(), Source.plainText, "Original",
                null, null, "2026-07-06T00:00:00Z", false));

        chats.updateTitle(chat.id(), "Renamed");
        chats.setArchived(chat.id(), true);
        chats.assignProject(chat.id(), null);

        Chat updated = chats.findById(chat.id()).orElseThrow();
        assertEquals("Renamed", updated.title());
        assertTrue(updated.archived());
        assertEquals(null, updated.projectId());

        chats.delete(chat.id());

        assertFalse(chats.findById(chat.id()).isPresent());
    }

    @Test
    void createsUpdatesDeletesAndSearchesMessages() throws Exception {
        Chat chat = chats.insert(new Chat(0, null, Source.plainText, "Searchable",
                null, null, "2026-07-06T00:00:00Z", false));
        Message first = messages.insert(new Message(0, chat.id(), "user",
                "storage foundation alpha", 0, null, null));
        Message second = messages.insert(new Message(0, chat.id(), "assistant",
                "repository beta", 1, null, null));

        assertEquals(List.of(first, second), messages.findByChat(chat.id()));
        assertEquals(List.of(first.id()), messages.searchText("alpha"));

        messages.updateText(first.id(), "storage foundation gamma");

        assertTrue(messages.searchText("alpha").isEmpty());
        assertEquals(List.of(first.id()), messages.searchText("gamma"));

        messages.delete(second.id());

        assertEquals(List.of(new Message(first.id(), chat.id(), "user",
                "storage foundation gamma", 0, null, null)), messages.findByChat(chat.id()));
        assertTrue(messages.searchText("beta").isEmpty());
    }

    @Test
    void assignsFindsRemovesAndCascadesTags() throws Exception {
        Chat chat = chats.insert(new Chat(0, null, Source.plainText, "Tagged",
                null, null, "2026-07-06T00:00:00Z", false));
        Tag tag = tags.insert(new Tag(0, "MVP"));

        assertEquals(tag, tags.findByName("mvp").orElseThrow());

        tags.assignToChat(chat.id(), tag.id());
        tags.assignToChat(chat.id(), tag.id());

        assertEquals(List.of(tag), tags.findByChat(chat.id()));

        tags.removeFromChat(chat.id(), tag.id());

        assertTrue(tags.findByChat(chat.id()).isEmpty());

        tags.assignToChat(chat.id(), tag.id());
        chats.delete(chat.id());

        assertTrue(tags.findByChat(chat.id()).isEmpty());
        assertEquals(tag, tags.findById(tag.id()).orElseThrow());
    }

    @Test
    void enforcesForeignKeysAndCaseInsensitiveUniqueTags() throws Exception {
        assertThrows(SQLException.class, () -> messages.insert(new Message(0, 999, "user",
                "orphan", 0, null, null)));

        tags.insert(new Tag(0, "SQLite"));

        assertThrows(SQLException.class, () -> tags.insert(new Tag(0, "sqlite")));
    }
}
