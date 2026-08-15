package chatmap.application.service;


import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;
import chatmap.application.port.ai.CommandBackedAiBackend;
import chatmap.application.port.ai.CommandBackedRun;
import chatmap.application.port.command.CommandResult;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PromptServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void submitExecutesBackendAndReturnsPromptResult() throws Exception {
        CapturingBackend backend = new CapturingBackend(
                new CommandBackedRun(
                        new AiResponse("OK\n", new BackendId("Fake CLI"), Duration.ofMillis(8)),
                        new CommandResult(0, "OK\n", "", Duration.ofMillis(8), false),
                        List.of("fake", "run")
                )
        );
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
        PromptService service = new PromptService(Map.of("fake", backend), null, null, clock, tempDir);

        PromptResult result = service.submit("fake", "Say exactly: OK");

        assertEquals("Fake CLI", result.backendLabel());
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
                Map.of("fake", backend), Map.of("fake", "Fake Label"), null, clock, tempDir);

        List<BackendDescriptor> backends = service.backends();

        assertEquals(1, backends.size());
        assertEquals("fake", backends.get(0).id());
        assertEquals("Fake Label", backends.get(0).label());
    }

    @Test
    void submitPersistsPromptAndResponseToDatabase() throws Exception {
        try (java.sql.Connection conn = new chatmap.infrastructure.persistence.sqlite.Database("jdbc:sqlite::memory:").openAndInitialize()) {
            chatmap.infrastructure.persistence.sqlite.ChatRepository chats = new chatmap.infrastructure.persistence.sqlite.ChatRepository(conn);
            chatmap.infrastructure.persistence.sqlite.MessageRepository messages = new chatmap.infrastructure.persistence.sqlite.MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages, new chatmap.infrastructure.importer.DefaultConversationFileReader());

            CapturingBackend backend = new CapturingBackend(
                    new CommandBackedRun(
                            new AiResponse("Claude answer", new BackendId("Claude CLI"), Duration.ofMillis(10)),
                            new CommandResult(0, "Claude answer", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "Test prompt")
                    )
            );
            Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
            PromptService service = new PromptService(Map.of("claude", backend), null, importService, clock, tempDir);

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
                            new AiResponse("First answer", new BackendId("Claude CLI"), Duration.ofMillis(10)),
                            new CommandResult(0, "First answer", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "First turn")
                    )
            );
            new PromptService(Map.of("claude", firstTurn), null, importService, clock, tempDir)
                    .submit("claude", "First turn", "session-abc");

            CapturingBackend secondTurn = new CapturingBackend(
                    new CommandBackedRun(
                            new AiResponse("Second answer", new BackendId("Claude CLI"), Duration.ofMillis(10)),
                            new CommandResult(0, "Second answer", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "Second turn")
                    )
            );
            new PromptService(Map.of("claude", secondTurn), null, importService, clock, tempDir)
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
                            new AiResponse("Answer A", new BackendId("Claude CLI"), Duration.ofMillis(10)),
                            new CommandResult(0, "Answer A", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "Prompt A")
                    )
            );
            new PromptService(Map.of("claude", backendA), null, importService, clock, tempDir)
                    .submit("claude", "Prompt A", "session-1");

            CapturingBackend backendB = new CapturingBackend(
                    new CommandBackedRun(
                            new AiResponse("Answer B", new BackendId("Claude CLI"), Duration.ofMillis(10)),
                            new CommandResult(0, "Answer B", "", Duration.ofMillis(10), false),
                            List.of("claude", "-p", "Prompt B")
                    )
            );
            new PromptService(Map.of("claude", backendB), null, importService, clock, tempDir)
                    .submit("claude", "Prompt B", "session-2");

            assertEquals(2, chats.findAll().size(), "different sessions must not be merged into one chat");
        }
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
                        new AiResponse("OK", new BackendId("Fake CLI"), Duration.ofMillis(1)),
                        new CommandResult(0, "OK", "", Duration.ofMillis(1), false),
                        List.of("fake", "run")
                )
        );
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
        PromptService service = new PromptService(Map.of("fake", backend), null, importService, clock, tempDir);

        org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                () -> service.submit("fake", "Say exactly: OK"));
    }

    @Test
    void submitSurvivesTranscriptWriteFailure() throws Exception {
        CapturingBackend backend = new CapturingBackend(
                new CommandBackedRun(
                        new AiResponse("OK", new BackendId("Fake CLI"), Duration.ofMillis(1)),
                        new CommandResult(0, "OK", "", Duration.ofMillis(1), false),
                        List.of("fake", "run")
                )
        );
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
        // A regular file where the transcript directory should be makes createDirectories fail.
        Path notADirectory = tempDir.resolve("blocked");
        Files.writeString(notADirectory, "occupied");
        PromptService service = new PromptService(Map.of("fake", backend), null, null, clock, notADirectory);

        PromptResult result = service.submit("fake", "Say exactly: OK");

        assertEquals("OK", result.response());
        assertTrue(result.transcript().isEmpty());
    }

    private static final class CapturingBackend implements CommandBackedAiBackend {
        private final CommandBackedRun run;
        private final Source source;
        AiRequest request;

        CapturingBackend(CommandBackedRun run) {
            this(run, Source.claudeCliPrompt);
        }

        CapturingBackend(CommandBackedRun run, Source source) {
            this.run = run;
            this.source = source;
        }

        @Override
        public Source source() {
            return source;
        }

        @Override
        public AiResponse ask(AiRequest request) {
            return askWithResult(request).response();
        }

        @Override
        public CommandBackedRun askWithResult(AiRequest request) {
            this.request = request;
            return run;
        }

        @Override
        public List<String> commandFor(AiRequest request) {
            return List.of("fake", "run");
        }
    }
}
