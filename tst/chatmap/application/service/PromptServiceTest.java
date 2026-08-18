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

import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.Channel;
import chatmap.application.port.llm.CommandBackedRun;
import chatmap.application.port.command.CommandResult;
import chatmap.domain.MessageRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PromptServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void submitExecutesBackendAndReturnsPromptResult() throws Exception {
        CapturingBackend backend = new CapturingBackend(
                new CommandBackedRun(
                        new LlmResponse("OK\n", new BackendId("Claude"), Duration.ofMillis(8),
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
                            new LlmResponse("Claude answer", new BackendId("Claude"), Duration.ofMillis(10),
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
            assertEquals(Channel.claudeCli.name(), storedChats.get(0).channelId());
            assertEquals(ModelTarget.claude.id(), storedChats.get(0).modelTargetId());
            assertEquals(ModelTarget.claude.providerModelName(), storedChats.get(0).providerModelName());
            assertNull(storedChats.get(0).providerSessionId());

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
                            new LlmResponse("First answer", new BackendId("Claude"), Duration.ofMillis(10),
                                    ModelTarget.claude, null),
                            new CommandResult(0, "First answer", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "First turn")
                    )
            );
            new PromptService(providers(firstTurn), importService, clock, tempDir)
                    .submit("claude", "First turn", "session-abc");

            CapturingBackend secondTurn = new CapturingBackend(
                    new CommandBackedRun(
                            new LlmResponse("Second answer", new BackendId("Claude"), Duration.ofMillis(10),
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
            assertEquals(Channel.claudeCli.name(), storedChats.get(0).channelId());
            assertEquals(ModelTarget.claude.id(), storedChats.get(0).modelTargetId());
            assertEquals("session-abc", storedChats.get(0).providerSessionId());
            assertNull(storedChats.get(0).externalConversationId(),
                    "generated prompt session identity must not occupy imported-chat external identity");

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
                            new LlmResponse("Answer A", new BackendId("Claude"), Duration.ofMillis(10),
                                    ModelTarget.claude, null),
                            new CommandResult(0, "Answer A", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "Prompt A")
                    )
            );
            new PromptService(providers(backendA), importService, clock, tempDir)
                    .submit("claude", "Prompt A", "session-1");

            CapturingBackend backendB = new CapturingBackend(
                    new CommandBackedRun(
                            new LlmResponse("Answer B", new BackendId("Claude"), Duration.ofMillis(10),
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
                            new LlmResponse("Created session answer", new BackendId("Claude"), Duration.ofMillis(10),
                                    ModelTarget.claude, "provider-session-1"),
                            new CommandResult(0, "Created session answer", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p")
                    )
            );

            PromptResult result = new PromptService(providers(backend), importService, clock, tempDir)
                    .submit("claude", "Start a session");

            assertEquals("provider-session-1", result.sessionId().orElseThrow());
            chatmap.domain.Chat stored = chats.findAll().getFirst();
            assertEquals(Channel.claudeCli.name(), stored.channelId());
            assertEquals(ModelTarget.claude.id(), stored.modelTargetId());
            assertEquals(ModelTarget.claude.providerModelName(), stored.providerModelName());
            assertEquals("provider-session-1", stored.providerSessionId());
            assertNull(stored.externalConversationId());
        }
    }

    @Test
    void sameProviderSessionIdOnDifferentTargetsCreatesSeparateChats() throws Exception {
        try (java.sql.Connection conn = new chatmap.infrastructure.persistence.sqlite.Database("jdbc:sqlite::memory:")
                .openAndInitialize()) {
            chatmap.infrastructure.persistence.sqlite.ChatRepository chats =
                    new chatmap.infrastructure.persistence.sqlite.ChatRepository(conn);
            chatmap.infrastructure.persistence.sqlite.MessageRepository messages =
                    new chatmap.infrastructure.persistence.sqlite.MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages,
                    new chatmap.infrastructure.importer.DefaultConversationFileReader());
            Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);

            CapturingBackend claude = new CapturingBackend(new CommandBackedRun(
                    new LlmResponse("Claude answer", new BackendId("Claude"), Duration.ofMillis(10),
                            ModelTarget.claude, null),
                    new CommandResult(0, "Claude answer", "", Duration.ofMillis(10), false),
                    List.of("claude", "-p")));
            CapturingBackend codex = new CapturingBackend(new CommandBackedRun(
                    new LlmResponse("Codex answer", new BackendId("Codex"), Duration.ofMillis(10),
                            ModelTarget.codex, null),
                    new CommandResult(0, "Codex answer", "", Duration.ofMillis(10), false),
                    List.of("codex", "exec")));

            Map<Channel, LlmProvider> configured = providers(Map.of(
                    Channel.claudeCli, claude,
                    Channel.codexCli, codex));
            new PromptService(configured, importService, clock, tempDir)
                    .submit("claude", "Claude turn", "shared-session");
            new PromptService(configured, importService, clock, tempDir)
                    .submit("codex", "Codex turn", "shared-session");

            List<chatmap.domain.Chat> storedChats = chats.findAll();
            assertEquals(2, storedChats.size(), "target identity scopes generated prompt sessions");
            assertEquals(List.of(ModelTarget.claude.id(), ModelTarget.codex.id()),
                    storedChats.stream().map(chatmap.domain.Chat::modelTargetId).toList());
            assertEquals(List.of("shared-session", "shared-session"),
                    storedChats.stream().map(chatmap.domain.Chat::providerSessionId).toList());
        }
    }

    @Test
    void listSessionsReturnsKnownDatabaseSessionsForTarget() throws Exception {
        try (java.sql.Connection conn = new chatmap.infrastructure.persistence.sqlite.Database("jdbc:sqlite::memory:")
                .openAndInitialize()) {
            chatmap.infrastructure.persistence.sqlite.ChatRepository chats =
                    new chatmap.infrastructure.persistence.sqlite.ChatRepository(conn);
            chatmap.infrastructure.persistence.sqlite.MessageRepository messages =
                    new chatmap.infrastructure.persistence.sqlite.MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages,
                    new chatmap.infrastructure.importer.DefaultConversationFileReader());
            Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);

            CapturingBackend claude = new CapturingBackend(new CommandBackedRun(
                    new LlmResponse("Claude answer", new BackendId("Claude"), Duration.ofMillis(10),
                            ModelTarget.claude, null),
                    new CommandResult(0, "Claude answer", "", Duration.ofMillis(10), false),
                    List.of("claude", "-p")));
            CapturingBackend codex = new CapturingBackend(new CommandBackedRun(
                    new LlmResponse("Codex answer", new BackendId("Codex"), Duration.ofMillis(10),
                            ModelTarget.codex, null),
                    new CommandResult(0, "Codex answer", "", Duration.ofMillis(10), false),
                    List.of("codex", "exec")));
            Map<Channel, LlmProvider> configured = providers(Map.of(
                    Channel.claudeCli, claude,
                    Channel.codexCli, codex));
            PromptService service = new PromptService(configured, importService, clock, tempDir);

            service.submit("claude", "First", "session-b");
            service.submit("claude", "Second", "session-a");
            service.submit("claude", "Third", "session-b");
            service.submit("codex", "Other target", "session-a");

            assertEquals(List.of("session-a", "session-b"), service.listSessions("claude"));
            assertEquals(List.of("session-a"), service.listSessions("codex"));
            assertEquals(List.of(), service.listSessions("agy"));
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
                    new LlmResponse("First answer", new BackendId("Claude"), Duration.ofMillis(10),
                            ModelTarget.claude, null),
                    new CommandResult(0, "First answer", "", Duration.ofMillis(10), false),
                    List.of("claude", "-p")))), importService, clock, tempDir)
                    .submit("claude", "First turn", "session-hash");
            new PromptService(providers(new CapturingBackend(new CommandBackedRun(
                    new LlmResponse("Second answer", new BackendId("Claude"), Duration.ofMillis(10),
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
                        new LlmResponse("OK", new BackendId("Claude"), Duration.ofMillis(1),
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
                        new LlmResponse("OK", new BackendId("Claude"), Duration.ofMillis(1),
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

    private static Map<Channel, LlmProvider> providers(LlmProvider claudeProvider) {
        return providers(Map.of(Channel.claudeCli, claudeProvider));
    }

    private static Map<Channel, LlmProvider> providers(Map<Channel, LlmProvider> configuredProviders) {
        EnumMap<Channel, LlmProvider> providers = new EnumMap<>(Channel.class);
        NoopProvider noop = new NoopProvider();
        for (Channel id : Channel.values()) {
            providers.put(id, noop);
        }
        providers.putAll(configuredProviders);
        return providers;
    }

    private static final class CapturingBackend implements LlmProvider {
        private final CommandBackedRun run;
        LlmRequest request;

        CapturingBackend(CommandBackedRun run) {
            this.run = run;
        }

        @Override
        public LlmResponse execute(ModelTarget target, LlmRequest request) {
            this.request = request;
            return run.response();
        }

        @Override
        public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
            return Set.of(chatmap.application.port.llm.LlmCapability.sessions);
        }
    }

    private static final class NoopProvider implements LlmProvider {
        @Override
        public LlmResponse execute(ModelTarget target, LlmRequest request) {
            throw new AssertionError("unexpected provider call for " + target);
        }

        @Override
        public Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
            return Set.of();
        }
    }
}
