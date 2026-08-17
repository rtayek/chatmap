package chatmap.presentation.cli;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.ProviderId;
import chatmap.app.bootstrap.LoggingBootstrap;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.application.service.PromptResult;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;

class RunPromptCliTest {

    private String originalLogDirectory;

    @BeforeEach
    void rememberLogDirectoryProperty() {
        originalLogDirectory = System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);
    }

    @AfterEach
    void releaseLogFileAndRestoreProperty() {
        LoggingBootstrap.initializeTemporaryFallback();
        restoreLogDirectoryProperty(originalLogDirectory);
    }

    private static void restoreLogDirectoryProperty(String value) {
        if (value == null) {
            System.clearProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);
        } else {
            System.setProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY, value);
        }
    }

    @Test
    void executeRunsPromptAndRecordsInDatabase(@TempDir Path tempDir) throws Exception {
        Path home = tempDir.resolve(".chatmap");
        Files.createDirectories(home);

        String[] cliArgs = new String[]{"--home", home.toString(), "claude", "Test prompt text"};
        Map<ProviderId, LlmProvider> backends = providers(new LlmProvider() {
            @Override
            public LlmResponse execute(ModelTarget target, LlmRequest request) {
                return new LlmResponse("Fake CLI response", new BackendId("Fake CLI"), Duration.ofMillis(5),
                        target, "session-123");
            }

            @Override
            public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
                return Set.of();
            }
        });
        Clock clock = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);

        PromptResult result = RunPromptCli.execute(cliArgs, backends, clock);

        assertEquals("Fake CLI", result.backendLabel());
        assertEquals("Fake CLI response", result.response());
        assertEquals(ProviderId.claudeCli.name(), result.providerId());
        assertEquals(ModelTarget.claude.id(), result.targetId());
        assertEquals(ModelTarget.claude.providerModelName(), result.providerModelName());
        assertEquals("session-123", result.sessionId().orElseThrow());
        Path transcript = result.transcript().orElseThrow();
        assertTrue(transcript.startsWith(home.resolve("transcripts")));
        assertTrue(Files.isRegularFile(transcript));
        assertTrue(Files.isDirectory(home.resolve("logs")));

        Path dbPath = home.resolve("chatmap.db");
        try (Connection conn = new Database("jdbc:sqlite:" + dbPath).openAndInitialize()) {
            ChatRepository chats = new ChatRepository(conn);
            MessageRepository messages = new MessageRepository(conn);

            List<Chat> storedChats = chats.findAll();
            assertEquals(1, storedChats.size());
            assertEquals("Test prompt text", storedChats.get(0).title());
            assertEquals(ProviderId.claudeCli.name(), storedChats.get(0).providerId());
            assertEquals(ModelTarget.claude.id(), storedChats.get(0).modelTargetId());
            assertEquals(ModelTarget.claude.providerModelName(), storedChats.get(0).providerModelName());
            assertEquals("session-123", storedChats.get(0).providerSessionId());

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

    private static Map<ProviderId, LlmProvider> providers(LlmProvider claudeProvider) {
        EnumMap<ProviderId, LlmProvider> providers = new EnumMap<>(ProviderId.class);
        LlmProvider noop = new LlmProvider() {
            @Override
            public LlmResponse execute(ModelTarget target, LlmRequest request) {
                throw new AssertionError("unexpected provider call for " + target);
            }

            @Override
            public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
                return Set.of();
            }
        };
        for (ProviderId id : ProviderId.values()) {
            providers.put(id, noop);
        }
        providers.put(ProviderId.claudeCli, claudeProvider);
        return providers;
    }
}
