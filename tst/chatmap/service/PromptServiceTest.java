package chatmap.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import chatmap.backend.AiRequest;
import chatmap.backend.AiResponse;
import chatmap.backend.BackendId;
import chatmap.backend.CommandBackedAiBackend;
import chatmap.backend.CommandBackedRun;
import chatmap.backend.CommandResult;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PromptServiceTest {

    @Test
    void submitExecutesBackendAndReturnsPromptResult() {
        CapturingBackend backend = new CapturingBackend(
                new CommandBackedRun(
                        new AiResponse("OK\n", new BackendId("Fake CLI"), Duration.ofMillis(8)),
                        new CommandResult(0, "OK\n", "", Duration.ofMillis(8), false),
                        List.of("fake", "run")
                )
        );
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
        PromptService service = new PromptService(Map.of("fake", backend), null, clock);

        PromptResult result = service.submit("fake", "Say exactly: OK");

        assertEquals("Fake CLI", result.backendLabel());
        assertEquals("OK\n", result.response());
        assertEquals("Say exactly: OK", backend.request.prompt());
    }

    @Test
    void listsAvailableBackends() {
        CapturingBackend backend = new CapturingBackend(null);
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
        PromptService service = new PromptService(Map.of("fake", backend), Map.of("fake", "Fake Label"), null, clock);

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
