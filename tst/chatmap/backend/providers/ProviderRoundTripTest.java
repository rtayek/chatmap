package chatmap.backend.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Chat;
import chatmap.domain.ConversationCandidate;
import chatmap.importer.ImportedChat;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.TagRepository;

/**
 * Full pipeline for a provider-sourced (as opposed to file-based; see
 * SampleRoundTripTest for that path) import: {@code provider.listChats()} ->
 * {@code provider.fetch(candidate)} -> {@code ImportService.persist(...)} ->
 * search -> {@code ExportService.exportChatMarkdown(...)} -> re-fetch/re-persist
 * dedup. This is the exact chain {@code ImportAllChatsCli} runs in production;
 * CliHistoryProvidersTest stops at fetch() and never proves anything downstream
 * of it works. One provider (in package scope, since the constructors that take
 * a custom root directory are package-private) per CLI-history provider.
 */
class ProviderRoundTripTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ImportService importService;
    private ExportService exportService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        importService = new ImportService(chats, messages);
        exportService = new ExportService(chats, messages, new ProjectRepository(conn), new TagRepository(conn));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void codexSessionFetchesPersistsSearchesAndExports(@TempDir Path root) throws Exception {
        Path file = root.resolve("rollout-gate.jsonl");
        Files.write(file, List.of(
                "{\"type\":\"session_meta\",\"payload\":{\"session_id\":\"abc\"}}",
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"user_message\","
                        + "\"message\":\"How do I run the gradle build gate?\"}}",
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"agent_message\","
                        + "\"message\":\"Run ./gradlew test checkstyleMain pmdMain spotbugsMain.\"}}"));

        roundTrip(new CodexCliHistoryProvider(root), "codexCli",
                "spotbugsMain", "How do I run the gradle build gate?",
                "Run ./gradlew test checkstyleMain pmdMain spotbugsMain.");
    }

    @Test
    void claudeCodeSessionFetchesPersistsSearchesAndExports(@TempDir Path root) throws Exception {
        Path file = root.resolve("session-fts.jsonl");
        Files.write(file, List.of(
                "{\"type\":\"ai-title\",\"aiTitle\":\"Full Text Search Question\"}",
                "{\"type\":\"user\",\"message\":{\"role\":\"user\","
                        + "\"content\":\"What does ChatMap use for full text search?\"}}",
                "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\",\"content\":"
                        + "[{\"type\":\"text\",\"text\":"
                        + "\"ChatMap uses SQLite FTS5 for full text search across imported messages.\"}]}}"));

        roundTrip(new ClaudeCodeHistoryProvider(root), "claudeCode",
                "FTS5", "What does ChatMap use for full text search?",
                "ChatMap uses SQLite FTS5 for full text search across imported messages.");
    }

    @Test
    void geminiCliSessionFetchesPersistsSearchesAndExports(@TempDir Path root) throws Exception {
        Path file = root.resolve("session-dedupe.jsonl");
        Files.write(file, List.of(
                "{\"sessionId\":\"a2a\",\"kind\":\"main\"}",
                "{\"$set\":{\"messages\":[{\"type\":\"user\",\"content\":"
                        + "[{\"text\":\"<session_context>\\nsetup</session_context>\"}]}],"
                        + "\"lastUpdated\":\"2026-08-06T21:33:48.859Z\"}}",
                "{\"id\":\"1\",\"timestamp\":\"2026-08-06T21:33:50Z\",\"type\":\"user\","
                        + "\"content\":[{\"text\":\"How does ChatMap dedupe imported chats?\"}]}",
                "{\"id\":\"2\",\"timestamp\":\"2026-08-06T21:33:52Z\",\"type\":\"gemini\","
                        + "\"content\":\"ChatMap dedupes by content hash within a source.\","
                        + "\"thoughts\":[],\"model\":\"gemini-3.5-flash\"}"));

        roundTrip(new GeminiCliHistoryProvider(root), "geminiCli",
                "dedupes", "How does ChatMap dedupe imported chats?",
                "ChatMap dedupes by content hash within a source.");
    }

    /**
     * Drives one provider through the full chain and asserts each stage, then
     * fetches and persists the same candidate a second time to prove the
     * provider path's dedup matches the file-import path (SampleRoundTripTest).
     */
    private void roundTrip(ChatProvider provider, String expectedSourceDbValue,
            String searchTerm, String userText, String assistantText) throws Exception {
        List<ConversationCandidate> candidates = provider.listChats();
        assertEquals(1, candidates.size(), "exactly one session file was written");

        ImportedChat imported = provider.fetch(candidates.get(0));
        ImportService.PersistResult firstResult = importService.persist(imported);
        assertEquals(ImportService.Outcome.inserted, firstResult.outcome());
        Chat chat = firstResult.chat();
        assertEquals(expectedSourceDbValue, chat.source().dbValue());

        List<Long> hits = messages.searchText(searchTerm);
        assertEquals(1, hits.size(), "search must find exactly the imported message");
        assertEquals(chat.id(), messages.findByChat(chat.id()).stream()
                .filter(message -> message.id() == hits.getFirst())
                .findFirst()
                .orElseThrow()
                .chatId());

        String markdown = exportService.exportChatMarkdown(chat.id()).orElseThrow();
        assertTrue(markdown.contains("Source: " + expectedSourceDbValue));
        assertTrue(markdown.contains("## user"));
        assertTrue(markdown.contains(userText));
        assertTrue(markdown.contains("## assistant"));
        assertTrue(markdown.contains(assistantText));

        // Re-fetch and re-persist the same candidate: the provider path's dedup
        // (by externalConversationId, see ImportService.persistWithExternalIdentity)
        // must behave the same as the file-import path already proven unchanged-safe.
        ImportedChat refetched = provider.fetch(candidates.get(0));
        ImportService.PersistResult secondResult = importService.persist(refetched);
        assertEquals(ImportService.Outcome.unchanged, secondResult.outcome());
        assertEquals(chat.id(), secondResult.chat().id());
    }
}
