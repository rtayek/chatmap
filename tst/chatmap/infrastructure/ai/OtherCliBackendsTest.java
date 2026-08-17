package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiBackendUnsupportedRequestException;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.OutputFormat;
import chatmap.application.port.ai.PermissionMode;

import chatmap.application.port.command.CommandResult;

import chatmap.application.port.command.CommandRequest;

import chatmap.application.port.command.CommandExecutor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import chatmap.domain.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OtherCliBackendsTest {
    private final CapturingExecutor executor = new CapturingExecutor();

    @Test
    void codexCliBackendConstructsCorrectCommand() {
        CodexCliProvider backend = new CodexCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Codex answer", "", Duration.ofMillis(10), false);

        AiResponse response = backend.execute(ModelTarget.codex, AiRequest.of("Explain recursion"));

        assertEquals("Codex answer", response.text());
        assertEquals("Codex", response.backendId().value());
        assertEquals(List.of("codex.cmd", "exec", "-"), executor.request.command());
        assertEquals("Explain recursion", executor.request.standardInput());
        // codexCli is CodexCliHistoryProvider's value for real imported sessions;
        // this backend's Q&A recordings must not share it (see Source's class doc).
        assertEquals(Source.codexCliPrompt, ModelTarget.codex.source());
        assertNotEquals(Source.codexCli, ModelTarget.codex.source());
    }

    @Test
    void codexCliBackendSupportsResumeSession() {
        CodexCliProvider backend = new CodexCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Resumed", "", Duration.ofMillis(5), false);

        backend.execute(ModelTarget.codex, AiRequest.withSession("Next step", "codex-sess-1"));

        assertEquals(List.of("codex.cmd", "exec", "resume", "codex-sess-1", "-"), executor.request.command());
        assertEquals("Next step", executor.request.standardInput());
    }

    @Test
    void codexExecutableSelectionIsPlatformSpecific() {
        assertEquals("codex.cmd", CodexCliProvider.executableName("Windows 11"));
        assertEquals("codex", CodexCliProvider.executableName("Linux"));
        assertEquals("codex", CodexCliProvider.executableName("Ubuntu on WSL"));
    }

    @Test
    void agyCliBackendConstructsCorrectCommand() {
        AntigravityCliProvider backend = new AntigravityCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Agy answer", "", Duration.ofMillis(15), false);

        AiResponse response = backend.execute(ModelTarget.agy, AiRequest.of("Hello Antigravity"));

        assertEquals("Agy answer", response.text());
        assertEquals("Antigravity", response.backendId().value());
        assertEquals(List.of("agy", "--print"), executor.request.command());
        assertEquals("Hello Antigravity", executor.request.standardInput());
        assertEquals(Source.agyCliPrompt, ModelTarget.agy.source());
    }

    @Test
    void agyCliBackendSupportsResumeSession() {
        AntigravityCliProvider backend = new AntigravityCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Resumed", "", Duration.ofMillis(5), false);

        backend.execute(ModelTarget.agy, AiRequest.withSession("Resume task", "agy-sess-99"));

        assertEquals(List.of("agy", "--conversation", "agy-sess-99", "--print"), executor.request.command());
        assertEquals("Resume task", executor.request.standardInput());
    }

    @Test
    void ollamaCliProviderPipesPromptToStandardInput() {
        OllamaCliProvider backend = new OllamaCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Ollama answer", "", Duration.ofMillis(20), false);

        AiResponse response = backend.execute(ModelTarget.ollama, AiRequest.of("What is Java?"));

        assertEquals("Ollama answer", response.text());
        assertEquals("Ollama llama3", response.backendId().value());
        assertEquals(List.of("ollama", "run", "llama3"), executor.request.command());
        assertEquals("What is Java?", executor.request.standardInput());
        // No importer produces plainText from an AI backend; Ollama previously fell through
        // to AiBackend's default source() (plainText), colliding with real .txt file imports.
        assertEquals(Source.ollamaPrompt, ModelTarget.ollama.source());
    }

    @Test
    void codexUsesItsOwnPermissionSyntaxAndRejectsUnsupportedStreamJson() {
        CodexCliProvider codex = new CodexCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "ok", "", Duration.ofMillis(5), false);

        codex.executeWithResult(ModelTarget.codex,
                AiRequest.of("hello").withPermissionMode(PermissionMode.unrestricted));
        assertEquals(List.of("codex.cmd", "exec", "--sandbox", "workspace-write", "-"),
                executor.request.command());

        assertThrows(AiBackendUnsupportedRequestException.class,
                () -> codex.executeWithResult(ModelTarget.codex,
                        AiRequest.of("hello").withOutputFormat(OutputFormat.streamJson)));
    }

    @Test
    void agySupportsPermissionAndStreamJsonUsingItsOwnFlags() {
        AntigravityCliProvider agy = new AntigravityCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "ok", "", Duration.ofMillis(5), false);

        agy.executeWithResult(ModelTarget.agy, AiRequest.of("hello")
                .withPermissionMode(PermissionMode.unrestricted)
                .withOutputFormat(OutputFormat.streamJson));

        assertEquals(List.of("agy", "--print", "--dangerously-skip-permissions",
                "--output-format", "stream-json"), executor.request.command());
    }

    @Test
    void ollamaCliProviderRejectsSystemPrompts() {
        OllamaCliProvider backend = new OllamaCliProvider(executor, Duration.ofSeconds(5));

        assertThrows(AiBackendUnsupportedRequestException.class,
                () -> backend.execute(ModelTarget.ollama, AiRequest.withSystemPrompt("Hi", "System instruction")));
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
