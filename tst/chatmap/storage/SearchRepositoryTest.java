package chatmap.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.SearchOptions;
import chatmap.domain.Source;
import chatmap.domain.Tag;

class SearchRepositoryTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ProjectRepository projects;
    private TagRepository tags;
    private SearchRepository search;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        projects = new ProjectRepository(conn);
        tags = new TagRepository(conn);
        search = new SearchRepository(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void insertedMessageIsSearchable() throws Exception {
        Chat chat = insertChat("Chat", null, false, "2026-07-06T00:00:00Z");
        messages.insert(new Message(0, chat.id(), "user", "alpha target", 0, null, null));

        assertEquals(List.of(chat), search.searchChatsByMessageText("target"));
    }

    @Test
    void updatedMessageTextUpdatesSearchResults() throws Exception {
        Chat chat = insertChat("Chat", null, false, "2026-07-06T00:00:00Z");
        Message message = messages.insert(new Message(0, chat.id(), "user", "old target", 0, null, null));

        messages.updateText(message.id(), "new target");

        assertTrue(search.searchChatsByMessageText("old").isEmpty());
        assertEquals(List.of(chat), search.searchChatsByMessageText("new"));
    }

    @Test
    void deletedMessageDisappearsFromSearchResults() throws Exception {
        Chat chat = insertChat("Chat", null, false, "2026-07-06T00:00:00Z");
        Message message = messages.insert(new Message(0, chat.id(), "user", "delete target", 0, null, null));

        messages.delete(message.id());

        assertTrue(search.searchChatsByMessageText("delete").isEmpty());
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        Chat chat = insertChat("Chat", null, false, "2026-07-06T00:00:00Z");
        messages.insert(new Message(0, chat.id(), "user", "ChatMap Target", 0, null, null));

        assertEquals(List.of(chat), search.searchChatsByMessageText("chatmap"));
        assertEquals(List.of(chat), search.searchChatsByMessageText("CHATMAP"));
    }

    @Test
    void multiWordQueryUsesFtsAndSemantics() throws Exception {
        Chat chat = insertChat("Chat", null, false, "2026-07-06T00:00:00Z");
        messages.insert(new Message(0, chat.id(), "user", "alpha beta gamma", 0, null, null));

        assertEquals(List.of(chat), search.searchChatsByMessageText("alpha beta"));
        assertTrue(search.searchChatsByMessageText("alpha missing").isEmpty());
    }

    @Test
    void archivedFilterWorks() throws Exception {
        Chat active = insertChat("Active", null, false, "2026-07-06T00:00:00Z");
        Chat archived = insertChat("Archived", null, true, "2026-07-06T00:01:00Z");
        messages.insert(new Message(0, active.id(), "user", "shared target", 0, null, null));
        messages.insert(new Message(0, archived.id(), "user", "shared target", 0, null, null));

        assertEquals(List.of(active), search.searchChatsByMessageText("target", new SearchOptions(null, null, false)));
        assertEquals(List.of(archived), search.searchChatsByMessageText("target", new SearchOptions(null, null, true)));
    }

    @Test
    void projectFilterWorks() throws Exception {
        Project project = projects.insert(new Project(0, "Project", null, "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));
        Chat match = insertChat("In Project", project.id(), false, "2026-07-06T00:00:00Z");
        Chat miss = insertChat("Outside", null, false, "2026-07-06T00:01:00Z");
        messages.insert(new Message(0, match.id(), "user", "project target", 0, null, null));
        messages.insert(new Message(0, miss.id(), "user", "project target", 0, null, null));

        assertEquals(List.of(match), search.searchChatsByMessageText("target", new SearchOptions(project.id(), null, null)));
    }

    @Test
    void tagFilterWorks() throws Exception {
        Chat match = insertChat("Tagged", null, false, "2026-07-06T00:00:00Z");
        Chat miss = insertChat("Untagged", null, false, "2026-07-06T00:01:00Z");
        Tag tag = tags.insert(new Tag(0, "MVP"));
        tags.assignToChat(match.id(), tag.id());
        messages.insert(new Message(0, match.id(), "user", "tag target", 0, null, null));
        messages.insert(new Message(0, miss.id(), "user", "tag target", 0, null, null));

        assertEquals(List.of(match), search.searchChatsByMessageText("target", new SearchOptions(null, tag.id(), null)));
    }

    @Test
    void projectAndTagFiltersWorkTogether() throws Exception {
        Project project = projects.insert(new Project(0, "Project", null, "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));
        Tag tag = tags.insert(new Tag(0, "MVP"));
        Chat match = insertChat("Match", project.id(), false, "2026-07-06T00:00:00Z");
        Chat projectOnly = insertChat("Project Only", project.id(), false, "2026-07-06T00:01:00Z");
        Chat tagOnly = insertChat("Tag Only", null, false, "2026-07-06T00:02:00Z");
        tags.assignToChat(match.id(), tag.id());
        tags.assignToChat(tagOnly.id(), tag.id());
        messages.insert(new Message(0, match.id(), "user", "combined target", 0, null, null));
        messages.insert(new Message(0, projectOnly.id(), "user", "combined target", 0, null, null));
        messages.insert(new Message(0, tagOnly.id(), "user", "combined target", 0, null, null));

        assertEquals(List.of(match), search.searchChatsByMessageText(
                "target", new SearchOptions(project.id(), tag.id(), null)));
    }

    @Test
    void emptyAndWhitespaceSearchReturnNoRepositoryMatches() throws Exception {
        assertTrue(search.searchChatsByMessageText("").isEmpty());
        assertTrue(search.searchChatsByMessageText("   ").isEmpty());
    }

    @Test
    void duplicateMessageMatchesReturnEachChatOnce() throws Exception {
        Chat chat = insertChat("Chat", null, false, "2026-07-06T00:00:00Z");
        messages.insert(new Message(0, chat.id(), "user", "duplicate target", 0, null, null));
        messages.insert(new Message(0, chat.id(), "assistant", "duplicate target", 1, null, null));

        assertEquals(List.of(chat), search.searchChatsByMessageText("target"));
    }

    private Chat insertChat(String title, Long projectId, boolean archived, String importedAt) throws Exception {
        return chats.insert(new Chat(0, projectId, Source.plainText, title, null, null, importedAt, archived));
    }
}
