package chatmap.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;

/**
 * Verifies the FTS5 external-content triggers in schema.sql.
 * These four behaviors are the riskiest part of the storage design
 * (design.md, "SQLite FTS5 Strategy"); everything else is plain CRUD.
 *
 * Uses a fresh in-memory database per test. The single Connection is shared
 * by all repositories because each :memory: connection is its own database.
 */
class MessageFtsTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    private Chat newChat() throws Exception {
        return chats.insert(new Chat(0, null, Source.unknown, "Test Chat",
                null, null, "2026-07-02T00:00:00Z", false));
    }

    private Message newMessage(long chatId, int seq, String text) throws Exception {
        return messages.insert(new Message(0, chatId, "user", text, seq, null, null));
    }

    @Test
    void insertedMessageBecomesSearchable() throws Exception {
        Chat chat = newChat();
        Message m = newMessage(chat.id(), 0, "the quick brown fox");

        List<Long> hits = messages.searchText("brown");
        assertEquals(List.of(m.id()), hits);
    }

    @Test
    void updatedTextUpdatesSearchResults() throws Exception {
        Chat chat = newChat();
        Message m = newMessage(chat.id(), 0, "original wording here");

        messages.updateText(m.id(), "completely revised phrasing");

        assertTrue(messages.searchText("original").isEmpty(), "old term must no longer match");
        assertEquals(List.of(m.id()), messages.searchText("revised"), "new term must match");
    }

    @Test
    void deletedMessageDisappearsFromSearch() throws Exception {
        Chat chat = newChat();
        Message m = newMessage(chat.id(), 0, "ephemeral content");

        messages.delete(m.id());

        assertTrue(messages.searchText("ephemeral").isEmpty());
    }

    @Test
    void deletingChatCascadesMessagesAndFtsIndex() throws Exception {
        Chat chat = newChat();
        newMessage(chat.id(), 0, "cascade target alpha");
        newMessage(chat.id(), 1, "cascade target beta");

        chats.delete(chat.id());

        assertTrue(messages.findByChat(chat.id()).isEmpty(), "messages must cascade-delete");
        assertTrue(messages.searchText("cascade").isEmpty(), "FTS index must follow the cascade");
        assertFalse(chats.findById(chat.id()).isPresent());
    }

    @Test
    void searchMatchesOnlyRelevantMessages() throws Exception {
        Chat chat = newChat();
        Message hit = newMessage(chat.id(), 0, "sqlite fts is neat");
        newMessage(chat.id(), 1, "unrelated text about java");

        assertEquals(List.of(hit.id()), messages.searchText("sqlite"));
    }
}
