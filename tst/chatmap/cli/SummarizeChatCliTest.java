package chatmap.cli;

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
import chatmap.cli.SummarizeChatCli.NoChatAvailableException;
import chatmap.cli.SummarizeChatCli.Resolution;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;
import chatmap.service.ImportService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

class SummarizeChatCliTest {

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

    // --- mostRecentChat (the local fallback) ---

    @Test
    void mostRecentChatIsEmptyWhenNoChats() throws Exception {
        assertTrue(SummarizeChatCli.mostRecentChat(chats).isEmpty(),
                "With no chats, the fallback should be empty");
    }

    @Test
    void mostRecentChatReturnsLatestImported() throws Exception {
        insertChat("Oldest", "2026-07-01T00:00:00Z");
        insertChat("Middle", "2026-07-15T00:00:00Z");
        Chat newest = insertChat("Newest", "2026-08-01T00:00:00Z");

        Optional<Chat> latest = SummarizeChatCli.mostRecentChat(chats);

        assertTrue(latest.isPresent());
        assertEquals(newest.id(), latest.get().id());
        assertEquals("Newest", latest.get().title());
    }

    // --- resolveChatId (the new provider-first default) ---

    @Test
    void explicitChatIdWinsOverProviders() throws Exception {
        ChatProvider provider = stubProvider("ShouldNotBeUsed", liveChat("Live"));

        Resolution resolution = SummarizeChatCli.resolveChatId(
                42L, List.of(provider), chats, importService);

        assertEquals(42L, resolution.chatId());
        // Provider must not have run: nothing imported.
        assertTrue(chats.findAll().isEmpty(), "Explicit id should not trigger a provider fetch");
    }

    @Test
    void defaultsToLastLiveProviderChatAndImportsIt() throws Exception {
        ChatProvider provider = stubProvider("Claude", liveChat("Yesterday's session"));

        Resolution resolution = SummarizeChatCli.resolveChatId(
                null, List.of(provider), chats, importService);

        List<Chat> stored = chats.findAll();
        assertEquals(1, stored.size(), "The live chat should have been imported");
        assertEquals(stored.get(0).id(), resolution.chatId());
        assertEquals("Yesterday's session", stored.get(0).title());
        assertTrue(resolution.how().contains("Claude"), resolution.how());
    }

    @Test
    void fallsBackToMostRecentWhenProviderHasNoLiveChat() throws Exception {
        insertChat("Stored", "2026-08-01T00:00:00Z");
        ChatProvider empty = stubProvider("Claude", null); // Optional.empty()

        Resolution resolution = SummarizeChatCli.resolveChatId(
                null, List.of(empty), chats, importService);

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

        Resolution resolution = SummarizeChatCli.resolveChatId(
                null, List.of(failing), chats, importService);

        assertEquals(stored.id(), resolution.chatId());
    }

    @Test
    void throwsWhenNoProviderChatAndNoStoredChats() {
        assertThrows(NoChatAvailableException.class, () ->
                SummarizeChatCli.resolveChatId(null, List.of(), chats, importService));
    }

    // --- helpers ---

    private Chat insertChat(String title, String importedAt) throws Exception {
        return chats.insert(new Chat(0, null, Source.plainText, title,
                null, null, importedAt, false));
    }

    /** A one-message imported chat, ready to persist. */
    private static ImportedChat liveChat(String title) {
        Chat chat = new Chat(0, null, Source.markdown, title,
                null, null, "2026-08-04T00:00:00Z", false);
        Message message = new Message(0, 0, "user", "hello from the provider", 0, null, null);
        return new ImportedChat(chat, List.of(message));
    }

    /** A provider returning the given imported chat, or empty when {@code live} is null. */
    private static ChatProvider stubProvider(String name, ImportedChat live) {
        return new ChatProvider() {
            @Override public String name() { return name; }
            @Override public Optional<ImportedChat> latestChat() {
                return Optional.ofNullable(live);
            }
        };
    }
}
