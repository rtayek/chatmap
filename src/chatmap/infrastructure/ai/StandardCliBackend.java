package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiBackendException;
import chatmap.application.port.ai.AiBackendExecutionException;
import chatmap.application.port.ai.AiBackendStartupException;
import chatmap.application.port.ai.AiBackendUnsupportedRequestException;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;
import chatmap.application.port.ai.CommandBackedAiBackend;
import chatmap.application.port.ai.CommandBackedRun;
import chatmap.application.port.ai.OutputFormat;
import chatmap.application.port.ai.PermissionMode;

import chatmap.application.port.command.CommandExecutionException;

import chatmap.application.port.command.CommandResult;

import chatmap.application.port.command.CommandRequest;

import chatmap.application.port.command.CommandExecutor;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chatmap.domain.Source;
import chatmap.infrastructure.command.CommandRunner;

/**
 * CLI-backed AI execution model for a locally installed agent binary
 * (claude, codex, agy). The three agents differ only in binary name,
 * {@link Source}, and (for claude) which {@link AiRequest} capabilities
 * {@link #commandFor} translates into flags -- construct via {@link #claude},
 * {@link #codex}, or {@link #agy} rather than subclassing.
 */
public final class StandardCliBackend implements CommandBackedAiBackend {

    private static final BackendId CLAUDE_BACKEND_ID = new BackendId("Claude CLI");
    private static final BackendId CODEX_BACKEND_ID = new BackendId("Codex CLI");
    private static final BackendId AGY_BACKEND_ID = new BackendId("Antigravity CLI");

    private final BackendId backendId;
    private final String binaryName;
    private final CommandExecutor commandExecutor;
    private final Duration timeout;
    private final Source source;

    public StandardCliBackend(
            BackendId backendId,
            String binaryName,
            CommandExecutor commandExecutor,
            Duration timeout,
            Source source) {
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.binaryName = Objects.requireNonNull(binaryName, "binaryName");
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.source = Objects.requireNonNull(source, "source");
    }

    public static StandardCliBackend claude(CommandExecutor commandExecutor, Duration timeout) {
        return new StandardCliBackend(CLAUDE_BACKEND_ID, "claude", commandExecutor, timeout, Source.claudeCliPrompt);
    }

    /** Convenience for callers that don't need to share a {@link CommandExecutor}. */
    public static StandardCliBackend claude(Duration timeout) {
        return claude(new CommandRunner(), timeout);
    }

    public static StandardCliBackend codex(CommandExecutor commandExecutor, Duration timeout) {
        return new StandardCliBackend(CODEX_BACKEND_ID, "codex", commandExecutor, timeout, Source.codexCliPrompt);
    }

    public static StandardCliBackend agy(CommandExecutor commandExecutor, Duration timeout) {
        return new StandardCliBackend(AGY_BACKEND_ID, "agy", commandExecutor, timeout, Source.agyCliPrompt);
    }

    public BackendId backendId() {
        return backendId;
    }

    public String binaryName() {
        return binaryName;
    }

    @Override
    public Source source() {
        return source;
    }

    /**
     * Distinguishes "genuinely zero sessions" (empty output, exit 0) from
     * "could not find out" (CLI missing, unauthenticated, nonzero exit) by
     * throwing on the latter instead of returning an empty list for both --
     * an empty list here used to be indistinguishable from a real failure.
     */
    @Override
    public List<String> listSessions() {
        CommandResult result;
        try {
            result = commandExecutor.run(new CommandRequest(
                    List.of(binaryName, "--list-sessions"), "", Duration.ofSeconds(10)));
        } catch (CommandExecutionException exception) {
            throw new AiBackendStartupException(
                    "Could not list sessions for " + backendId.value() + ": " + exception.getMessage(),
                    backendId, exception);
        }
        if (result.exitCode() != 0) {
            throw new AiBackendExecutionException(
                    backendId.value() + " --list-sessions exited with status " + result.exitCode()
                            + (result.standardError().isBlank() ? "" : ": " + result.standardError().strip()),
                    backendId, result);
        }
        if (result.standardOutput().isBlank()) {
            return List.of();
        }
        return result.standardOutput().lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .distinct()
                .toList();
    }

    @Override
    public AiResponse ask(AiRequest request) {
        return askWithResult(request).response();
    }

    @Override
    public String ask(String prompt) throws IOException {
        try {
            return ask(AiRequest.of(prompt)).text().strip();
        } catch (AiBackendException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * {@code permissionMode}/{@code outputFormat} only translate to flags for
     * {@code claude} -- confirmed live that {@code --dangerously-skip-permissions}
     * is what makes {@code claude -p} actually edit files instead of exiting 0
     * with no changes, and {@code --output-format stream-json} requires
     * {@code --verbose} or the CLI rejects the invocation outright. The flag
     * names and semantics for other agents (codex, agy, ...) haven't been
     * verified for this {@code <binary> -p} invocation shape, so a request for
     * either capability is silently a no-op there rather than passing an
     * unrecognized flag -- one silent no-op failure mode is preferable to
     * trading it for a loud one on an unverified CLI.
     */
    @Override
    public List<String> commandFor(AiRequest request) {
        Objects.requireNonNull(request, "request");
        List<String> command = new ArrayList<>();
        command.add(binaryName);
        if (request.sessionId().isPresent() && !request.sessionId().get().isBlank()) {
            command.add("--resume");
            command.add(request.sessionId().get());
        }
        command.add("-p");
        if ("claude".equals(binaryName)) {
            if (request.permissionMode() == PermissionMode.unrestricted) {
                command.add("--dangerously-skip-permissions");
            }
            if (request.outputFormat() == OutputFormat.streamJson) {
                command.add("--output-format");
                command.add("stream-json");
                command.add("--verbose");
            }
        }
        return command;
    }

    @Override
    public CommandBackedRun askWithResult(AiRequest request) {
        return execute(commandExecutor, backendId, timeout, request, commandFor(request));
    }

    /**
     * Shared subprocess execution and result/error mapping for every
     * command-backed AI backend (claude/codex/agy here, and
     * {@link OllamaCliBackend}) -- none currently support a system prompt,
     * so that's rejected unconditionally rather than through a per-backend
     * override no implementation ever set to {@code true}.
     */
    static CommandBackedRun execute(CommandExecutor commandExecutor, BackendId backendId, Duration timeout,
            AiRequest request, List<String> command) {
        Objects.requireNonNull(request, "request");
        if (request.systemPrompt().isPresent()) {
            throw new AiBackendUnsupportedRequestException(
                    backendId.value() + " backend does not support system prompts yet.", backendId);
        }

        CommandResult result;
        try {
            result = commandExecutor.run(new CommandRequest(
                    command, request.effectivePrompt(), timeout, request.workingDirectory().orElse(null)));
        } catch (CommandExecutionException exception) {
            throw new AiBackendStartupException(
                    "Could not start " + backendId.value() + ": " + exception.getMessage(), backendId, exception);
        }

        if (result.timedOut()) {
            throw new AiBackendExecutionException(backendId.value() + " timed out after " + timeout, backendId, result);
        }
        if (result.exitCode() != 0) {
            throw new AiBackendExecutionException(nonzeroExitMessage(backendId, result), backendId, result);
        }

        AiResponse response = new AiResponse(result.standardOutput(), backendId, result.duration());
        return new CommandBackedRun(response, result, command);
    }

    private static String nonzeroExitMessage(BackendId backendId, CommandResult result) {
        String message = backendId.value() + " exited with status " + result.exitCode();
        if (!result.standardError().isBlank()) {
            return message + ": " + result.standardError().strip();
        }
        return message;
    }
}
