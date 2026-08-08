package chatmap.service;

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

import chatmap.backend.AiRequest;
import chatmap.backend.AiResponse;
import chatmap.backend.BackendId;
import chatmap.backend.CommandBackedAiBackend;
import chatmap.backend.CommandBackedRun;
import chatmap.backend.CommandResult;
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
        try (java.sql.Connection conn = new chatmap.storage.Database("jdbc:sqlite::memory:").openAndInitialize()) {
            chatmap.storage.ChatRepository chats = new chatmap.storage.ChatRepository(conn);
            chatmap.storage.MessageRepository messages = new chatmap.storage.MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages);

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
            assertEquals(chatmap.domain.Source.claudeCode, storedChats.get(0).source());

            List<chatmap.domain.Message> storedMessages = messages.findByChat(storedChats.get(0).id());
            assertEquals(2, storedMessages.size());
            assertEquals("user", storedMessages.get(0).role());
            assertEquals("Test prompt", storedMessages.get(0).text());
            assertEquals("assistant", storedMessages.get(1).role());
            assertEquals("Claude answer", storedMessages.get(1).text());
        }
    }

    private static final class CapturingBackend implements CommandBackedAiBackend {
        private final CommandBackedRun run;
        private final Source source;
        AiRequest request;

        CapturingBackend(CommandBackedRun run) {
            this(run, Source.claudeCode);
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
