package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.OutputFormat;
import chatmap.application.port.ai.PermissionMode;
import chatmap.application.port.ai.PromptProfile;

import chatmap.application.port.command.CommandResult;

import chatmap.application.port.command.CommandRequest;

import chatmap.application.port.command.CommandExecutor;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import chatmap.domain.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClaudeCliBackendTest {
    private final CapturingExecutor executor = new CapturingExecutor();
    private final ClaudeCliProvider backend = new ClaudeCliProvider(executor, Duration.ofSeconds(5));

    @Test
    void sourceIsDistinctFromImportedClaudeCodeTranscripts() {
        // claudeCode is ClaudeCodeHistoryProvider's value for real imported sessions;
        // this backend's Q&A recordings must not share it (see Source's class doc).
        assertEquals(Source.claudeCliPrompt, ModelTarget.claude.source());
        assertNotEquals(Source.claudeCode, ModelTarget.claude.source());
    }

    @Test
    void successfulOutputBecomesAiResponseText() {
        executor.result = new CommandResult(0,
                "{\"type\":\"result\",\"session_id\":\"claude-session-123\",\"result\":\"OK\"}\n",
                "", Duration.ofMillis(12), false);

        AiResponse response = backend.execute(ModelTarget.claude, AiRequest.of("Say exactly: OK"));

        assertEquals("OK", response.text());
        assertEquals("claude-session-123", response.sessionId().orElseThrow());
        assertEquals("Claude", response.backendId().value());
        assertEquals(Duration.ofMillis(12), response.duration());
    }

    @Test
    void streamJsonSessionIdAndAssistantTextAreReturnedWhenProviderCreatesOne() {
        executor.result = new CommandResult(0,
                "{\"type\":\"system\",\"session_id\":\"claude-session-123\"}\n"
                        + "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}]}}\n",
                "", Duration.ofMillis(12), false);

        AiResponse response = backend.execute(ModelTarget.claude,
                AiRequest.of("Say exactly: OK").withOutputFormat(OutputFormat.streamJson));

        assertEquals("OK", response.text());
        assertEquals("claude-session-123", response.sessionId().orElseThrow());
    }

    @Test
    void constructsClaudePrintCommandWithPromptOnStandardInput() {
        String prompt = "quotes \" semicolon ; dollars $HOME backticks `x` newline\nend";
        executor.result = new CommandResult(0, "done", "", Duration.ofMillis(1), false);

        backend.execute(ModelTarget.claude, AiRequest.of(prompt));

        assertEquals(List.of("claude", "-p", "--output-format", "json"), executor.request.command());
        assertEquals(prompt, executor.request.standardInput());
    }

    @Test
    void constructsClaudeResumeCommandWhenSessionIdIsPresent() {
        executor.result = new CommandResult(0, "resumed response", "", Duration.ofMillis(1), false);

        backend.execute(ModelTarget.claude, AiRequest.withSession("Continue work", "sess-123-abc"));

        assertEquals(List.of("claude", "--resume", "sess-123-abc", "-p", "--output-format", "json"),
                executor.request.command());
        assertEquals("Continue work", executor.request.standardInput());
    }

    @Test
    void guidedTeachingProfileAddsTeachingInstructionToClaudePromptArgument() {
        executor.result = new CommandResult(0, "done", "", Duration.ofMillis(1), false);

        backend.execute(ModelTarget.claude,
                AiRequest.withProfile("Help me understand fractions", PromptProfile.guidedTeaching));

        assertEquals(List.of("claude", "-p", "--output-format", "json"), executor.request.command());
        assertTrue(executor.request.standardInput().contains("[GUIDED_TEACHING mode]"));
        assertTrue(executor.request.standardInput().contains("Help the learner understand"));
        assertTrue(executor.request.standardInput().contains("Match any requested technical level"));
        assertTrue(executor.request.standardInput().contains("Help me understand fractions"));
    }

    @Test
    void nonzeroCommandResultBecomesClearBackendFailure() {
        executor.result = new CommandResult(7, "", "bad credentials", Duration.ofMillis(2), false);

        chatmap.application.port.ai.AiBackendException exception = assertThrows(
                chatmap.application.port.ai.AiBackendException.class,
                () -> backend.execute(ModelTarget.claude, AiRequest.of("hello")));

        assertEquals("Claude exited with status 7: bad credentials", exception.getMessage());
        assertEquals("bad credentials", exception.commandResult().orElseThrow().standardError());
    }

    @Test
    void timeoutIsReportedClearly() {
        executor.result = new CommandResult(-1, "", "partial", Duration.ofSeconds(5), true);

        chatmap.application.port.ai.AiBackendException exception = assertThrows(
                chatmap.application.port.ai.AiBackendException.class,
                () -> backend.execute(ModelTarget.claude, AiRequest.of("hello")));

        assertTrue(exception.getMessage().contains("timed out"));
        assertTrue(exception.commandResult().orElseThrow().timedOut());
    }

    @Test
    void stderrIsRetainedInFailureDiagnostics() {
        executor.result = new CommandResult(1, "partial", "provider error", Duration.ofMillis(4), false);

        chatmap.application.port.ai.AiBackendException exception = assertThrows(
                chatmap.application.port.ai.AiBackendException.class,
                () -> backend.execute(ModelTarget.claude, AiRequest.of("hello")));

        assertEquals("provider error", exception.commandResult().orElseThrow().standardError());
    }

    @Test
    void unrestrictedPermissionModeAddsSkipPermissionsFlag() {
        executor.result = new CommandResult(0, "done", "", Duration.ofMillis(1), false);

        backend.executeWithResult(ModelTarget.claude,
                AiRequest.of("hello").withPermissionMode(PermissionMode.unrestricted));

        assertEquals(List.of("claude", "-p", "--dangerously-skip-permissions", "--output-format", "json"),
                executor.request.command());
    }

    @Test
    void streamJsonOutputFormatRequiresVerboseFlag() {
        executor.result = new CommandResult(0, "done", "", Duration.ofMillis(1), false);

        backend.executeWithResult(ModelTarget.claude, AiRequest.of("hello").withOutputFormat(OutputFormat.streamJson));

        assertEquals(List.of("claude", "-p", "--output-format", "stream-json", "--verbose"),
                executor.request.command());
    }

    @Test
    void defaultPermissionModeAndOutputFormatAddNoExtraFlags() {
        executor.result = new CommandResult(0, "done", "", Duration.ofMillis(1), false);

        backend.execute(ModelTarget.claude, AiRequest.of("hello"));

        assertEquals(List.of("claude", "-p", "--output-format", "json"), executor.request.command());
    }

    @Test
    void workingDirectoryIsPassedThroughToTheCommandRequest() {
        executor.result = new CommandResult(0, "done", "", Duration.ofMillis(1), false);
        Path worktree = Path.of("some", "worktree");

        backend.executeWithResult(ModelTarget.claude, AiRequest.of("hello").withWorkingDirectory(worktree));

        assertEquals(worktree, executor.request.workingDirectory());
    }

    @Test
    void noWorkingDirectoryRequestLeavesItNull() {
        executor.result = new CommandResult(0, "done", "", Duration.ofMillis(1), false);

        backend.execute(ModelTarget.claude, AiRequest.of("hello"));

        assertNull(executor.request.workingDirectory());
    }

    @Test
    void listSessionsIsNotALiveUndocumentedCliCall() {
        assertEquals(List.of(), backend.listSessions(ModelTarget.claude));
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
