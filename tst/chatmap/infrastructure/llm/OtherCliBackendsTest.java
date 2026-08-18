package chatmap.infrastructure.llm;

import chatmap.application.port.llm.LlmBackendUnsupportedRequestException;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.PermissionMode;
import chatmap.application.port.llm.OutputFormat;

import chatmap.application.port.command.CommandResult;

import chatmap.application.port.command.CommandRequest;

import chatmap.application.port.command.CommandExecutor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import chatmap.domain.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OtherCliBackendsTest {
    private final CapturingExecutor executor = new CapturingExecutor();

    @Test
    void codexCliBackendConstructsCorrectCommand() {
        CodexCliProvider backend = new CodexCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0,
                "{\"type\":\"thread.started\",\"thread_id\":\"codex-session-1\"}\n"
                        + "{\"type\":\"item.completed\","
                        + "\"item\":{\"type\":\"agent_message\",\"text\":\"Codex answer\"}}\n",
                "", Duration.ofMillis(10), false);

        LlmResponse response = backend.execute(ModelTarget.codex, LlmRequest.of("Explain recursion"));

        assertEquals("Codex answer", response.text());
        assertEquals("codex-session-1", response.sessionId().orElseThrow());
        assertEquals("Codex", response.backendId().value());
        assertEquals(List.of("codex.cmd", "exec", "--json", "-"), executor.request.command());
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

        backend.execute(ModelTarget.codex, LlmRequest.withSession("Next step", "codex-sess-1"));

        assertEquals(List.of("codex.cmd", "exec", "--json", "resume", "codex-sess-1", "-"),
                executor.request.command());
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
        executor.result = new CommandResult(0,
                "{\"type\":\"result\",\"conversation_id\":\"agy-session-1\",\"result\":\"Agy answer\"}\n",
                "", Duration.ofMillis(15), false);

        LlmResponse response = backend.execute(ModelTarget.agy, LlmRequest.of("Hello Antigravity"));

        assertEquals("Agy answer", response.text());
        assertEquals("agy-session-1", response.sessionId().orElseThrow());
        assertEquals("Antigravity", response.backendId().value());
        assertEquals(List.of("agy", "--output-format", "json", "--print", "Hello Antigravity"),
                executor.request.command());
        assertEquals("Hello Antigravity", executor.request.standardInput());
        assertEquals(Source.agyCliPrompt, ModelTarget.agy.source());
    }

    @Test
    void agyCliBackendSupportsResumeSession() {
        AntigravityCliProvider backend = new AntigravityCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "Resumed", "", Duration.ofMillis(5), false);

        backend.execute(ModelTarget.agy, LlmRequest.withSession("Resume task", "agy-sess-99"));

        assertEquals(List.of("agy", "--conversation", "agy-sess-99", "--output-format", "json",
                "--print", "Resume task"), executor.request.command());
        assertEquals("Resume task", executor.request.standardInput());
    }

    @Test
    void codexUsesItsOwnPermissionSyntaxAndRejectsUnsupportedStreamJson() {
        CodexCliProvider codex = new CodexCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "ok", "", Duration.ofMillis(5), false);

        codex.executeWithResult(ModelTarget.codex,
                LlmRequest.of("hello").withPermissionMode(PermissionMode.unrestricted));
        assertEquals(List.of("codex.cmd", "exec", "--json", "--sandbox", "workspace-write", "-"),
                executor.request.command());

        assertThrows(LlmBackendUnsupportedRequestException.class,
                () -> codex.executeWithResult(ModelTarget.codex,
                        LlmRequest.of("hello").withOutputFormat(OutputFormat.streamJson)));
    }

    @Test
    void agySupportsPermissionAndStreamJsonUsingItsOwnFlags() {
        AntigravityCliProvider agy = new AntigravityCliProvider(executor, Duration.ofSeconds(5));
        executor.result = new CommandResult(0, "ok", "", Duration.ofMillis(5), false);

        agy.executeWithResult(ModelTarget.agy, LlmRequest.of("hello")
                .withPermissionMode(PermissionMode.unrestricted)
                .withOutputFormat(OutputFormat.streamJson));

        assertEquals(List.of("agy", "--dangerously-skip-permissions", "--output-format", "stream-json",
                "--print", "hello"), executor.request.command());
    }

    @Test
    void sessionListingDoesNotShellOutForCodexOrAntigravity() {
        assertEquals(List.of(), new CodexCliProvider(executor, Duration.ofSeconds(5)).listSessions(ModelTarget.codex));
        assertEquals(List.of(), new AntigravityCliProvider(executor, Duration.ofSeconds(5)).listSessions(ModelTarget.agy));
        assertNull(executor.request);
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
