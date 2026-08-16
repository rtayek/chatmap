package chatmap.application.service;


import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;
import chatmap.application.port.ai.AiProvider;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.ProviderId;
import chatmap.application.port.ai.CommandBackedRun;
import chatmap.application.port.command.CommandResult;
import chatmap.domain.MessageRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PromptServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void submitExecutesBackendAndReturnsPromptResult() throws Exception {
        CapturingBackend backend = new CapturingBackend(
                new CommandBackedRun(
                        new AiResponse("OK\n", new BackendId("Claude"), Duration.ofMillis(8),
                                ModelTarget.claude, null),
                        new CommandResult(0, "OK\n", "", Duration.ofMillis(8), false),
                        List.of("fake", "run")
                )
        );
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
        PromptService service = new PromptService(providers(backend), null, clock, tempDir);

        PromptResult result = service.submit("claude", "Say exactly: OK");

        assertEquals("Claude", result.backendLabel());
        assertEquals("OK\n", result.response());
        assertEquals("Say exactly: OK", backend.request.prompt());
        assertEquals(tempDir.resolve("prompt-1786017600000.md").toAbsolutePath().normalize(),
                result.transcriptPath());
        String transcript = Files.readString(result.transcriptPath());
        assertTrue(transcript.contains("## USER:\nSay exactly: OK"));
        assertTrue(transcript.contains("## ASSISTANT:\nOK\n"));
    }

    @Test
    void listsAvailableBackends() {
        CapturingBackend backend = new CapturingBackend(null);
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
        PromptService service = new PromptService(
                providers(backend), null, clock, tempDir);

        List<BackendDescriptor> backends = service.backends();

        assertEquals(ModelTarget.values().length, backends.size());
        assertEquals("claude", backends.get(0).id());
        assertEquals("Claude", backends.get(0).label());
    }

    @Test
    void submitPersistsPromptAndResponseToDatabase() throws Exception {
        try (java.sql.Connection conn = new chatmap.infrastructure.persistence.sqlite.Database("jdbc:sqlite::memory:").openAndInitialize()) {
            chatmap.infrastructure.persistence.sqlite.ChatRepository chats = new chatmap.infrastructure.persistence.sqlite.ChatRepository(conn);
            chatmap.infrastructure.persistence.sqlite.MessageRepository messages = new chatmap.infrastructure.persistence.sqlite.MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());

            CapturingBackend backend = new CapturingBackend(
                    new CommandBackedRun(
                            new AiResponse("Claude answer", new BackendId("Claude"), Duration.ofMillis(10),
                                    ModelTarget.claude, null),
                            new CommandResult(0, "Claude answer", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "Test prompt")
                    )
            );
            Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
            PromptService service = new PromptService(providers(backend), importService, clock, tempDir);

            service.submit("claude", "Test prompt");

            List<chatmap.domain.Chat> storedChats = chats.findAll();
            assertEquals(1, storedChats.size());
            assertEquals("Test prompt", storedChats.get(0).title());
            assertEquals(chatmap.domain.Source.claudeCliPrompt, storedChats.get(0).source());

            List<chatmap.domain.Message> storedMessages = messages.findByChat(storedChats.get(0).id());
            assertEquals(2, storedMessages.size());
            assertEquals(MessageRole.user, storedMessages.get(0).role());
            assertEquals("Test prompt", storedMessages.get(0).text());
            assertEquals(MessageRole.assistant, storedMessages.get(1).role());
            assertEquals("Claude answer", storedMessages.get(1).text());
        }
    }

    @Test
    void submitWithSameSessionIdAppendsToOneChat() throws Exception {
        try (java.sql.Connection conn = new chatmap.infrastructure.persistence.sqlite.Database("jdbc:sqlite::memory:").openAndInitialize()) {
            chatmap.infrastructure.persistence.sqlite.ChatRepository chats = new chatmap.infrastructure.persistence.sqlite.ChatRepository(conn);
            chatmap.infrastructure.persistence.sqlite.MessageRepository messages = new chatmap.infrastructure.persistence.sqlite.MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());
            Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);

            CapturingBackend firstTurn = new CapturingBackend(
                    new CommandBackedRun(
                            new AiResponse("First answer", new BackendId("Claude"), Duration.ofMillis(10),
                                    ModelTarget.claude, null),
                            new CommandResult(0, "First answer", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "First turn")
                    )
            );
            new PromptService(providers(firstTurn), importService, clock, tempDir)
                    .submit("claude", "First turn", "session-abc");

            CapturingBackend secondTurn = new CapturingBackend(
                    new CommandBackedRun(
                            new AiResponse("Second answer", new BackendId("Claude"), Duration.ofMillis(10),
                                    ModelTarget.claude, null),
                            new CommandResult(0, "Second answer", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "Second turn")
                    )
            );
            new PromptService(providers(secondTurn), importService, clock, tempDir)
                    .submit("claude", "Second turn", "session-abc");

            List<chatmap.domain.Chat> storedChats = chats.findAll();
            assertEquals(1, storedChats.size(), "both turns of the same session should share one chat");
            assertEquals("First turn", storedChats.get(0).title(),
                    "title should come from the first turn, not be overwritten by later ones");

            List<chatmap.domain.Message> storedMessages = messages.findByChat(storedChats.get(0).id());
            assertEquals(4, storedMessages.size(), "both turns' messages should be preserved, not replaced");
            assertEquals("First turn", storedMessages.get(0).text());
            assertEquals("First answer", storedMessages.get(1).text());
            assertEquals("Second turn", storedMessages.get(2).text());
            assertEquals("Second answer", storedMessages.get(3).text());
        }
    }

    @Test
    void submitWithDifferentSessionIdsCreatesSeparateChats() throws Exception {
        try (java.sql.Connection conn = new chatmap.infrastructure.persistence.sqlite.Database("jdbc:sqlite::memory:").openAndInitialize()) {
            chatmap.infrastructure.persistence.sqlite.ChatRepository chats = new chatmap.infrastructure.persistence.sqlite.ChatRepository(conn);
            chatmap.infrastructure.persistence.sqlite.MessageRepository messages = new chatmap.infrastructure.persistence.sqlite.MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());
            Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);

            CapturingBackend backendA = new CapturingBackend(
                    new CommandBackedRun(
                            new AiResponse("Answer A", new BackendId("Claude"), Duration.ofMillis(10),
                                    ModelTarget.claude, null),
                            new CommandResult(0, "Answer A", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "Prompt A")
                    )
            );
            new PromptService(providers(backendA), importService, clock, tempDir)
                    .submit("claude", "Prompt A", "session-1");

            CapturingBackend backendB = new CapturingBackend(
                    new CommandBackedRun(
                            new AiResponse("Answer B", new BackendId("Claude"), Duration.ofMillis(10),
                                    ModelTarget.claude, null),
                            new CommandResult(0, "Answer B", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "Prompt B")
                    )
            );
            new PromptService(providers(backendB), importService, clock, tempDir)
                    .submit("claude", "Prompt B", "session-2");

            assertEquals(2, chats.findAll().size(), "different sessions must not be merged into one chat");
        }
    }

    @Test
    void providerCreatedSessionIdIsPersistedAndReturned() throws Exception {
        try (java.sql.Connection conn = new chatmap.infrastructure.persistence.sqlite.Database("jdbc:sqlite::memory:").openAndInitialize()) {
            chatmap.infrastructure.persistence.sqlite.ChatRepository chats = new chatmap.infrastructure.persistence.sqlite.ChatRepository(conn);
            chatmap.infrastructure.persistence.sqlite.MessageRepository messages = new chatmap.infrastructure.persistence.sqlite.MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());
            Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
            CapturingBackend backend = new CapturingBackend(
                    new CommandBackedRun(
                            new AiResponse("Created session answer", new BackendId("Claude"), Duration.ofMillis(10),
                                    ModelTarget.claude, "provider-session-1"),
                            new CommandResult(0, "Created session answer", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p")
                    )
            );

            PromptResult result = new PromptService(providers(backend), importService, clock, tempDir)
                    .submit("claude", "Start a session");

            assertEquals("provider-session-1", result.sessionId().orElseThrow());
            assertEquals("provider-session-1", chats.findAll().getFirst().externalConversationId());
        }
    }

    @Test
    void appendedSessionKeepsContentHashConsistentWithFullTranscript() throws Exception {
        try (java.sql.Connection conn = new chatmap.infrastructure.persistence.sqlite.Database("jdbc:sqlite::memory:").openAndInitialize()) {
            chatmap.infrastructure.persistence.sqlite.ChatRepository chats = new chatmap.infrastructure.persistence.sqlite.ChatRepository(conn);
            chatmap.infrastructure.persistence.sqlite.MessageRepository messages = new chatmap.infrastructure.persistence.sqlite.MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());
            Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);

            new PromptService(providers(new CapturingBackend(new CommandBackedRun(
                    new AiResponse("First answer", new BackendId("Claude"), Duration.ofMillis(10),
                            ModelTarget.claude, null),
                    new CommandResult(0, "First answer", "", Duration.ofMillis(10), false),
                    List.of("claude", "-p")))), importService, clock, tempDir)
                    .submit("claude", "First turn", "session-hash");
            new PromptService(providers(new CapturingBackend(new CommandBackedRun(
                    new AiResponse("Second answer", new BackendId("Claude"), Duration.ofMillis(10),
                            ModelTarget.claude, null),
                    new CommandResult(0, "Second answer", "", Duration.ofMillis(10), false),
                    List.of("claude", "-p")))), importService, clock, tempDir)
                    .submit("claude", "Second turn", "session-hash");

            chatmap.domain.Chat stored = chats.findAll().getFirst();
            assertEquals(ChatContentHasher.hash(messages.findByChat(stored.id())), stored.contentHash());
        }
    }

    @Test
    void unknownTargetFailsWithoutInvokingProvider() {
        CapturingBackend backend = new CapturingBackend(null);
        PromptService service = new PromptService(providers(backend), null,
                Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC), tempDir);

        assertThrows(IllegalArgumentException.class, () -> service.submit("frog", "ribbit"));
        assertEquals(null, backend.request);
    }

    @Test
    void submitFailsWhenDatabaseWriteFails() throws Exception {
        java.sql.Connection conn = new chatmap.infrastructure.persistence.sqlite.Database("jdbc:sqlite::memory:").openAndInitialize();
        ImportService importService = new ImportService(
                new chatmap.infrastructure.persistence.sqlite.ChatRepository(conn),
                new chatmap.infrastructure.persistence.sqlite.MessageRepository(conn));
        conn.close();

        CapturingBackend backend = new CapturingBackend(
                new CommandBackedRun(
                        new AiResponse("OK", new BackendId("Claude"), Duration.ofMillis(1),
                                ModelTarget.claude, null),
                        new CommandResult(0, "OK", "", Duration.ofMillis(1), false),
                        List.of("fake", "run")
                )
        );
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
        PromptService service = new PromptService(providers(backend), importService, clock, tempDir);

        org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                () -> service.submit("claude", "Say exactly: OK"));
    }

    @Test
    void submitSurvivesTranscriptWriteFailure() throws Exception {
        CapturingBackend backend = new CapturingBackend(
                new CommandBackedRun(
                        new AiResponse("OK", new BackendId("Claude"), Duration.ofMillis(1),
                                ModelTarget.claude, null),
                        new CommandResult(0, "OK", "", Duration.ofMillis(1), false),
                        List.of("fake", "run")
                )
        );
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
        // A regular file where the transcript directory should be makes createDirectories fail.
        Path notADirectory = tempDir.resolve("blocked");
        Files.writeString(notADirectory, "occupied");
        PromptService service = new PromptService(providers(backend), null, clock, notADirectory);

        PromptResult result = service.submit("claude", "Say exactly: OK");

        assertEquals("OK", result.response());
        assertTrue(result.transcript().isEmpty());
    }

    private static Map<ProviderId, AiProvider> providers(AiProvider claudeProvider) {
        EnumMap<ProviderId, AiProvider> providers = new EnumMap<>(ProviderId.class);
        NoopProvider noop = new NoopProvider();
        for (ProviderId id : ProviderId.values()) {
            providers.put(id, noop);
        }
        providers.put(ProviderId.claudeCli, claudeProvider);
        return providers;
    }

    private static final class CapturingBackend implements AiProvider {
        private final CommandBackedRun run;
        AiRequest request;

        CapturingBackend(CommandBackedRun run) {
            this.run = run;
        }

        @Override
        public AiResponse execute(ModelTarget target, AiRequest request) {
            this.request = request;
            return run.response();
        }

        @Override
        public Set<chatmap.application.port.ai.AiCapability> capabilities(ModelTarget target) {
            return Set.of(chatmap.application.port.ai.AiCapability.sessions);
        }
    }

    private static final class NoopProvider implements AiProvider {
        @Override
        public AiResponse execute(ModelTarget target, AiRequest request) {
            throw new AssertionError("unexpected provider call for " + target);
        }

        @Override
        public Set<chatmap.application.port.ai.AiCapability> capabilities(ModelTarget target) {
            return Set.of();
        }
    }
}
