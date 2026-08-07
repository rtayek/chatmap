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
        PromptService service = new PromptService(Map.of("fake", backend), null, clock, tempDir);

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

    private static final class CapturingBackend implements CommandBackedAiBackend {
        private final CommandBackedRun run;
        AiRequest request;

        CapturingBackend(CommandBackedRun run) {
            this.run = run;
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
