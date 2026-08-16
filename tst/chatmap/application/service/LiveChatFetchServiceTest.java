package chatmap.application.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.application.port.provider.ChatProvider;
import chatmap.application.port.provider.ChatProviderException;
import chatmap.domain.Chat;
import chatmap.domain.ImportMetadata;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;
import chatmap.application.model.ImportedChat;
import chatmap.application.service.LiveChatFetchService.NoChatAvailableException;
import chatmap.application.service.LiveChatFetchService.Resolution;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;

class LiveChatFetchServiceTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ImportService importService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());
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
    void consumesSearchableChatsFromAllSixProviderSources() throws Exception {
        List<ProviderCase> providerCases = List.of(
                new ProviderCase("Claude (web)", Source.claudeWeb, "claude-web-1",
                        "https://claude.ai/chat/claude-web-1"),
                new ProviderCase("ChatGPT (web)", Source.chatGptWeb, "chatgpt-web-1",
                        "https://chatgpt.com/c/chatgpt-web-1"),
                new ProviderCase("Gemini (web)", Source.geminiWeb, "gemini-web-1",
                        "https://gemini.google.com/app/gemini-web-1"),
                new ProviderCase("Claude Code (CLI)", Source.claudeCode, "project-a/session-1.jsonl",
                        "file:///sessions/claude-code/project-a/session-1.jsonl"),
                new ProviderCase("Codex (CLI)", Source.codexCli, "2026/08/04/rollout-1.jsonl",
                        "file:///sessions/codex/2026/08/04/rollout-1.jsonl"),
                new ProviderCase("Gemini (CLI)", Source.geminiCli, "project-hash/chats/session-1.jsonl",
                        "file:///sessions/gemini/project-hash/chats/session-1.jsonl"));

        for (ProviderCase providerCase : providerCases) {
            String token = providerCase.source().dbValue().toLowerCase(java.util.Locale.ROOT) + "token";
            ImportedChat imported = providerChat(providerCase.source(), providerCase.externalId(),
                    providerCase.sourceUri(), providerCase.name(), token);

            Resolution resolution = service(stubProvider(providerCase.name(), imported)).resolve(null);

            Chat stored = chats.findById(resolution.chatId()).orElseThrow();
            assertEquals(providerCase.source(), stored.source(), providerCase.name());
            assertEquals(providerCase.externalId(), stored.externalConversationId(), providerCase.name());
            assertEquals(providerCase.sourceUri(), stored.sourceUri(), providerCase.name());
            assertEquals(providerCase.name(), stored.title(), providerCase.name());
            assertEquals(List.of("Question with " + token, "Answer with " + token),
                    messages.findByChat(stored.id()).stream().map(Message::text).toList(),
                    providerCase.name());
            assertEquals(2, messages.searchText(token).size(), providerCase.name());
            assertTrue(resolution.how().contains(providerCase.name()), resolution.how());
        }

        assertEquals(providerCases.size(), chats.findAll().size());
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
            @Override public Optional<ImportedChat> latestChat() throws ChatProviderException {
                throw new ChatProviderException("network down");
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
        return chats.insert(Chat.builder()
                                    .id(0)
                                    .projectId(null)
                                    .source(Source.plainText)
                                    .title(title)
                                    .createdAt(null)
                                    .updatedAt(null)
                                    .importedAt(importedAt)
                                    .archived(false)
                                    .build());
    }

    private static ImportedChat liveChat(String title) {
        Chat chat = Chat.builder()
                            .id(0)
                            .projectId(null)
                            .source(Source.markdown)
                            .title(title)
                            .createdAt(null)
                            .updatedAt(null)
                            .importedAt("2026-08-04T00:00:00Z")
                            .archived(false)
                            .build();
        Message message = new Message(0, 0, MessageRole.user, "hello from the provider", 0, null, null);
        return new ImportedChat(chat, List.of(message));
    }

    private static ImportedChat providerChat(Source source, String externalId, String sourceUri,
            String title, String token) {
        Chat chat = Chat.builder()
                .id(0)
                .source(source)
                .title(title)
                .importedAt("2026-08-04T00:00:00Z")
                .externalConversationId(externalId)
                .sourceUri(sourceUri)
                .lastImportedAt("2026-08-04T00:00:00Z")
                .build();
        return new ImportedChat(chat, List.of(
                new Message(0, 0, MessageRole.user, "Question with " + token, 0, null, null),
                new Message(0, 0, MessageRole.assistant, "Answer with " + token, 1, null, null)));
    }

    private static ChatProvider stubProvider(String name, ImportedChat live) {
        return new ChatProvider() {
            @Override public String name() { return name; }
            @Override public Optional<ImportedChat> latestChat() {
                return Optional.ofNullable(live);
            }
        };
    }

    private record ProviderCase(String name, Source source, String externalId, String sourceUri) {
    }
}
