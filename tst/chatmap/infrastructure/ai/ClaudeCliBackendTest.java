package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiBackendException;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.PromptProfile;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClaudeCliBackendTest {
    private final CapturingExecutor executor = new CapturingExecutor();
    private final ClaudeCliBackend backend = new ClaudeCliBackend(executor, Duration.ofSeconds(5));

    @Test
    void sourceIsDistinctFromImportedClaudeCodeTranscripts() {
        // claudeCode is ClaudeCodeHistoryProvider's value for real imported sessions;
        // this backend's Q&A recordings must not share it (see Source's class doc).
        assertEquals(Source.claudeCliPrompt, backend.source());
        assertNotEquals(Source.claudeCode, backend.source());
    }

    @Test
    void successfulOutputBecomesAiResponseText() {
        executor.result = new CommandResult(0, "OK\n", "", Duration.ofMillis(12), false);

        AiResponse response = backend.ask(AiRequest.of("Say exactly: OK"));

        assertEquals("OK\n", response.text());
        assertEquals("Claude CLI", response.backendId().value());
        assertEquals(Duration.ofMillis(12), response.duration());
    }

    @Test
    void constructsClaudePrintCommandWithPromptOnStandardInput() {
        String prompt = "quotes \" semicolon ; dollars $HOME backticks `x` newline\nend";
        executor.result = new CommandResult(0, "done", "", Duration.ofMillis(1), false);

        backend.ask(AiRequest.of(prompt));

        assertEquals(List.of("claude", "-p"), executor.request.command());
        assertEquals(prompt, executor.request.standardInput());
    }

    @Test
    void constructsClaudeResumeCommandWhenSessionIdIsPresent() {
        executor.result = new CommandResult(0, "resumed response", "", Duration.ofMillis(1), false);

        backend.ask(AiRequest.withSession("Continue work", "sess-123-abc"));

        assertEquals(List.of("claude", "--resume", "sess-123-abc", "-p"), executor.request.command());
        assertEquals("Continue work", executor.request.standardInput());
    }

    @Test
    void guidedTeachingProfileAddsTeachingInstructionToClaudePromptArgument() {
        executor.result = new CommandResult(0, "done", "", Duration.ofMillis(1), false);

        backend.ask(AiRequest.withProfile("Help me understand fractions", PromptProfile.guidedTeaching));

        assertEquals(List.of("claude", "-p"), executor.request.command());
        assertTrue(executor.request.standardInput().contains("[GUIDED_TEACHING mode]"));
        assertTrue(executor.request.standardInput().contains("Help the learner understand"));
        assertTrue(executor.request.standardInput().contains("Match any requested technical level"));
        assertTrue(executor.request.standardInput().contains("Help me understand fractions"));
    }

    @Test
    void nonzeroCommandResultBecomesClearBackendFailure() {
        executor.result = new CommandResult(7, "", "bad credentials", Duration.ofMillis(2), false);

        AiBackendException exception = assertThrows(AiBackendException.class, () -> backend.ask(AiRequest.of("hello")));

        assertEquals("Claude CLI exited with status 7: bad credentials", exception.getMessage());
        assertEquals("bad credentials", exception.commandResult().orElseThrow().standardError());
    }

    @Test
    void timeoutIsReportedClearly() {
        executor.result = new CommandResult(-1, "", "partial", Duration.ofSeconds(5), true);

        AiBackendException exception = assertThrows(AiBackendException.class, () -> backend.ask(AiRequest.of("hello")));

        assertTrue(exception.getMessage().contains("timed out"));
        assertTrue(exception.commandResult().orElseThrow().timedOut());
    }

    @Test
    void stderrIsRetainedInFailureDiagnostics() {
        executor.result = new CommandResult(1, "partial", "provider error", Duration.ofMillis(4), false);

        AiBackendException exception = assertThrows(AiBackendException.class, () -> backend.ask(AiRequest.of("hello")));

        assertEquals("provider error", exception.commandResult().orElseThrow().standardError());
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
