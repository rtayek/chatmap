package chatmap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.SearchRepository;

class SearchServiceTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private SearchService searchService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        searchService = new SearchService(chats, new SearchRepository(conn));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void searchesMessageTextAndReturnsMatchingChats() throws Exception {
        Chat match = insertChat("Match", "2026-07-06T00:00:00Z");
        Chat miss = insertChat("Miss", "2026-07-06T00:01:00Z");
        messages.insert(new Message(0, match.id(), "user", "ChatMap search target", 0, null, null));
        messages.insert(new Message(0, miss.id(), "user", "unrelated content", 0, null, null));

        assertEquals(List.of(match), searchService.searchChats("target"));
    }

    @Test
    void emptySearchReturnsAllChats() throws Exception {
        Chat first = insertChat("First", "2026-07-06T00:00:00Z");
        Chat second = insertChat("Second", "2026-07-06T00:01:00Z");

        assertEquals(List.of(first, second), searchService.searchChats("   "));
    }

    private Chat insertChat(String title, String importedAt) throws Exception {
        return chats.insert(new Chat(0, null, Source.plainText, title, null, null, importedAt, false));
    }
}
