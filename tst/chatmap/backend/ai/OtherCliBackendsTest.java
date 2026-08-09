package chatmap.backend.ai;

import chatmap.backend.command.CommandResult;

import chatmap.backend.command.CommandRequest;

import chatmap.backend.command.CommandExecutor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OtherCliBackendsTest {
    private final CapturingExecutor executor = new CapturingExecutor();

    @Test
    void codexCliBackendConstructsCorrectCommand() {
        CodexCliBackend backend = new CodexCliBackend(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Codex answer", "", Duration.ofMillis(10), false);

        AiResponse response = backend.ask(AiRequest.of("Explain recursion"));

        assertEquals("Codex answer", response.text());
        assertEquals("Codex CLI", response.backendId().value());
        assertEquals(List.of("codex", "-p"), executor.request.command());
        assertEquals("Explain recursion", executor.request.standardInput());
    }

    @Test
    void codexCliBackendSupportsResumeSession() {
        CodexCliBackend backend = new CodexCliBackend(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Resumed", "", Duration.ofMillis(5), false);

        backend.ask(AiRequest.withSession("Next step", "codex-sess-1"));

        assertEquals(List.of("codex", "--resume", "codex-sess-1", "-p"), executor.request.command());
        assertEquals("Next step", executor.request.standardInput());
    }

    @Test
    void agyCliBackendConstructsCorrectCommand() {
        AgyCliBackend backend = new AgyCliBackend(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Agy answer", "", Duration.ofMillis(15), false);

        AiResponse response = backend.ask(AiRequest.of("Hello Antigravity"));

        assertEquals("Agy answer", response.text());
        assertEquals("Antigravity CLI", response.backendId().value());
        assertEquals(List.of("agy", "-p"), executor.request.command());
        assertEquals("Hello Antigravity", executor.request.standardInput());
    }

    @Test
    void agyCliBackendSupportsResumeSession() {
        AgyCliBackend backend = new AgyCliBackend(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Resumed", "", Duration.ofMillis(5), false);

        backend.ask(AiRequest.withSession("Resume task", "agy-sess-99"));

        assertEquals(List.of("agy", "--resume", "agy-sess-99", "-p"), executor.request.command());
        assertEquals("Resume task", executor.request.standardInput());
    }

    @Test
    void ollamaCliBackendPipesPromptToStandardInput() {
        OllamaCliBackend backend = new OllamaCliBackend(executor, Duration.ofSeconds(5), "llama3");
        executor.result = new CommandResult(0, "Ollama answer", "", Duration.ofMillis(20), false);

        AiResponse response = backend.ask(AiRequest.of("What is Java?"));

        assertEquals("Ollama answer", response.text());
        assertEquals("Ollama llama3", response.backendId().value());
        assertEquals(List.of("ollama", "run", "llama3"), executor.request.command());
        assertEquals("What is Java?", executor.request.standardInput());
    }

    @Test
    void ollamaCliBackendRejectsSystemPrompts() {
        OllamaCliBackend backend = new OllamaCliBackend(executor, Duration.ofSeconds(5), "llama3");

        assertThrows(AiBackendUnsupportedRequestException.class,
                () -> backend.ask(AiRequest.withSystemPrompt("Hi", "System instruction")));
    }

    private static final class CapturingExecutor implements CommandExecutor {
        CommandRequest request;
        CommandResult result;

        @Override
        public CommandResult run(CommandRequest request) {
            this.request = request;
            return result;
        }
    }
}
