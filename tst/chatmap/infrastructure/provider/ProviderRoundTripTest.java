package chatmap.infrastructure.provider;

import chatmap.application.port.provider.ChatProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Chat;
import chatmap.domain.ConversationCandidate;
import chatmap.application.model.ImportedChat;
import chatmap.application.service.ExportService;
import chatmap.application.service.ImportService;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.TagRepository;

/**
 * Full pipeline for a provider-sourced (as opposed to file-based; see
 * SampleRoundTripTest for that path) import: {@code provider.listChats()} ->
 * {@code provider.fetch(candidate)} -> {@code ImportService.persist(...)} ->
 * search -> {@code ExportService.exportChatMarkdown(...)} -> re-fetch/re-persist
 * dedup. This is the exact chain {@code ImportAllChatsCli} runs in production;
 * CliHistoryProvidersTest stops at fetch() and never proves anything downstream
 * of it works. One provider (in package scope, since the constructors that take
 * a custom root directory are package-private) per CLI-history provider.
 *
 * Also covers the reverse direction: does a fresh {@code listChats()} pass
 * recognize an already-persisted chat via {@code findImportedIdsByExternalIdentity}
 * without needing to fetch it again (the pre-fetch skip {@code ImportAllChatsCli}
 * actually relies on for cheap re-runs, a different code path from persist-time
 * dedup) -- see {@code freshDiscoveryRecognizesAnAlreadyPersistedCandidate...}.
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
        importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());
        exportService = new ExportService(chats, messages, new ProjectRepository(conn), new TagRepository(conn), new chatmap.infrastructure.exporter.MarkdownExporter(), new chatmap.infrastructure.exporter.HandoffExporter());
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
     * The reverse direction from the tests above: given a chat already persisted,
     * does a fresh discovery pass recognize it without calling fetch() again?
     *
     * {@code ImportAllChatsCli}'s efficiency on a re-run over a large local
     * history doesn't come from persist-time dedup (proven by the
     * {@code roundTrip} helper's re-fetch/re-persist step above) -- that path
     * never runs unless fetch() is called at all. It comes from
     * {@code ChatRepository.findImportedIdsByExternalIdentity}, checked against
     * bare {@code listChats()} metadata *before* fetch() is ever called. That's
     * a genuinely different code path; nothing above exercises it.
     */
    @Test
    void freshDiscoveryRecognizesAnAlreadyPersistedCandidateWithoutFetchingItAgain(@TempDir Path root) throws Exception {
        Path persistedFile = root.resolve("rollout-persisted.jsonl");
        Files.write(persistedFile, List.of(
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"user_message\","
                        + "\"message\":\"Is this chat already imported?\"}}",
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"agent_message\","
                        + "\"message\":\"Yes, once persisted.\"}}"));

        CodexCliHistoryProvider provider = new CodexCliHistoryProvider(root);
        ConversationCandidate persistedCandidate = provider.listChats().get(0);
        Chat persistedChat = importService.persist(provider.fetch(persistedCandidate)).chat();

        // A second session file appears in the same directory, never fetched or persisted.
        Path newFile = root.resolve("rollout-new.jsonl");
        Files.write(newFile, List.of(
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"user_message\",\"message\":\"A brand new question.\"}}",
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"agent_message\",\"message\":\"A brand new answer.\"}}"));

        // Fresh discovery pass over the directory -- this must recognize the persisted
        // candidate from metadata alone, with no fetch() call in between.
        List<ConversationCandidate> freshCandidates = provider.listChats();
        assertEquals(2, freshCandidates.size());

        Map<String, Long> importedIds = chats.findImportedIdsByExternalIdentity(freshCandidates);

        String persistedKey = ChatRepository.identityKey(
                persistedCandidate.source(), persistedCandidate.externalConversationId());
        assertEquals(persistedChat.id(), importedIds.get(persistedKey));

        ConversationCandidate newCandidate = freshCandidates.stream()
                .filter(candidate -> !candidate.externalConversationId()
                        .equals(persistedCandidate.externalConversationId()))
                .findFirst().orElseThrow();
        String newKey = ChatRepository.identityKey(newCandidate.source(), newCandidate.externalConversationId());
        assertFalse(importedIds.containsKey(newKey), "a never-persisted candidate must not show up as imported");
        assertEquals(1, importedIds.size(), "only the already-persisted candidate should be recognized");
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
