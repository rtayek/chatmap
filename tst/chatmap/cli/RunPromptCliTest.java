package chatmap.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.backend.AiBackend;
import chatmap.backend.AiResponse;
import chatmap.backend.BackendId;
import chatmap.backend.DefaultAiBackends;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.service.PromptResult;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

class RunPromptCliTest {

    @Test
    void defaultAiBackendsInstantiatesSupportedBackends() {
        Map<String, AiBackend> backends = DefaultAiBackends.defaults();
        assertTrue(backends.containsKey("claude"));
        assertTrue(backends.containsKey("codex"));
        assertTrue(backends.containsKey("agy"));
        assertTrue(backends.containsKey("ollama"));
    }

    @Test
    void executeRunsPromptAndRecordsInDatabase(@TempDir Path tempDir) throws Exception {
        Path home = tempDir.resolve(".chatmap");
        Files.createDirectories(home);

        String[] cliArgs = new String[]{"--home", home.toString(), "fake", "Test prompt text"};
        Map<String, AiBackend> backends = Map.of("fake", request ->
                new AiResponse("Fake CLI response", new BackendId("Fake CLI"), Duration.ofMillis(5)));
        Clock clock = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);

        PromptResult result = RunPromptCli.execute(cliArgs, backends, clock);

        assertEquals("Fake CLI", result.backendLabel());
        assertEquals("Fake CLI response", result.response());

        Path dbPath = home.resolve("chatmap.db");
        try (Connection conn = new Database("jdbc:sqlite:" + dbPath).openAndInitialize()) {
            ChatRepository chats = new ChatRepository(conn);
            MessageRepository messages = new MessageRepository(conn);

            List<Chat> storedChats = chats.findAll();
            assertEquals(1, storedChats.size());
            assertEquals("Test prompt text", storedChats.get(0).title());

            List<Message> storedMessages = messages.findByChat(storedChats.get(0).id());
            assertEquals(2, storedMessages.size());
            assertEquals("Test prompt text", storedMessages.get(0).text());
            assertEquals("Fake CLI response", storedMessages.get(1).text());
        }
    }

    @Test
    void executeRejectsInsufficientArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                RunPromptCli.execute(new String[]{"claude"}, Map.of(), Clock.systemUTC()));
    }
}
