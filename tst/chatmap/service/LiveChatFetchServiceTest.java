package chatmap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.backend.ChatProvider;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;
import chatmap.service.LiveChatFetchService.NoChatAvailableException;
import chatmap.service.LiveChatFetchService.Resolution;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

class LiveChatFetchServiceTest {

    private Connection conn;
    private ChatRepository chats;
    private ImportService importService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        importService = new ImportService(chats, new MessageRepository(conn));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    private LiveChatFetchService service(ChatProvider... providers) {
        return new LiveChatFetchService(List.of(providers), importService, chats);
    }

    // --- mostRecentChat (the local fallback) ---

    @Test
    void mostRecentChatIsEmptyWhenNoChats() throws Exception {
        assertTrue(service().mostRecentChat().isEmpty());
    }

    @Test
    void mostRecentChatReturnsLatestImported() throws Exception {
        insertChat("Oldest", "2026-07-01T00:00:00Z");
        insertChat("Middle", "2026-07-15T00:00:00Z");
        Chat newest = insertChat("Newest", "2026-08-01T00:00:00Z");

        Optional<Chat> latest = service().mostRecentChat();

        assertTrue(latest.isPresent());
        assertEquals(newest.id(), latest.get().id());
        assertEquals("Newest", latest.get().title());
    }

    // --- resolve (the provider-first fallback order) ---

    @Test
    void explicitChatIdWinsOverProviders() throws Exception {
        Resolution resolution = service(stubProvider("ShouldNotBeUsed", liveChat("Live"))).resolve(42L);

        assertEquals(42L, resolution.chatId());
        assertTrue(chats.findAll().isEmpty(), "explicit id should not trigger a provider fetch");
    }

    @Test
    void defaultsToLastLiveProviderChatAndImportsIt() throws Exception {
        Resolution resolution = service(stubProvider("Claude", liveChat("Yesterday's session"))).resolve(null);

        List<Chat> stored = chats.findAll();
        assertEquals(1, stored.size());
        assertEquals(stored.get(0).id(), resolution.chatId());
        assertEquals("Yesterday's session", stored.get(0).title());
        assertTrue(resolution.how().contains("Claude"), resolution.how());
    }

    @Test
    void fallsBackToMostRecentWhenProviderHasNoLiveChat() throws Exception {
        insertChat("Stored", "2026-08-01T00:00:00Z");
        Resolution resolution = service(stubProvider("Claude", null)).resolve(null);

        assertEquals("Stored", chats.findById(resolution.chatId()).orElseThrow().title());
        assertTrue(resolution.how().contains("most recent"), resolution.how());
    }

    @Test
    void fallsBackToMostRecentWhenProviderThrows() throws Exception {
        Chat stored = insertChat("Stored", "2026-08-01T00:00:00Z");
        ChatProvider failing = new ChatProvider() {
            @Override public String name() { return "Flaky"; }
            @Override public Optional<ImportedChat> latestChat() throws Exception {
                throw new java.io.IOException("network down");
            }
        };

        assertEquals(stored.id(), service(failing).resolve(null).chatId());
    }

    @Test
    void throwsWhenNoProviderChatAndNoStoredChats() {
        assertThrows(NoChatAvailableException.class, () -> service().resolve(null));
    }

    // --- helpers ---

    private Chat insertChat(String title, String importedAt) throws Exception {
        return chats.insert(new Chat(0, null, Source.plainText, title, null, null, importedAt, false));
    }

    private static ImportedChat liveChat(String title) {
        Chat chat = new Chat(0, null, Source.markdown, title, null, null, "2026-08-04T00:00:00Z", false);
        Message message = new Message(0, 0, "user", "hello from the provider", 0, null, null);
        return new ImportedChat(chat, List.of(message));
    }

    private static ChatProvider stubProvider(String name, ImportedChat live) {
        return new ChatProvider() {
            @Override public String name() { return name; }
            @Override public Optional<ImportedChat> latestChat() {
                return Optional.ofNullable(live);
            }
        };
    }
}
