package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiBackendException;
import chatmap.application.port.ai.AiBackendExecutionException;
import chatmap.application.port.ai.AiBackendStartupException;
import chatmap.application.port.ai.AiBackendUnsupportedRequestException;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;

import chatmap.application.port.command.CommandExecutionException;

import chatmap.application.port.command.CommandResult;

import chatmap.application.port.command.CommandRequest;

import chatmap.application.port.command.CommandExecutor;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import chatmap.domain.Source;

/**
 * Common base class for CLI-backed AI execution models (e.g. claude, codex, agy).
 */
public class StandardCliBackend implements CommandBackedAiBackend {

    static final BackendId CLAUDE_BACKEND_ID = new BackendId("Claude CLI");
    static final BackendId CODEX_BACKEND_ID = new BackendId("Codex CLI");
    static final BackendId AGY_BACKEND_ID = new BackendId("Antigravity CLI");

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

    @Override
    public List<String> listSessions() {
        try {
            CommandResult result = commandExecutor.run(new CommandRequest(
                    List.of(binaryName, "--list-sessions"), "", Duration.ofSeconds(10)));
            if (result.exitCode() != 0 || result.standardOutput().isBlank()) {
                return List.of();
            }
            return result.standardOutput().lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .distinct()
                    .toList();
        } catch (RuntimeException failure) {
            return List.of();
        }
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

    boolean supportsSystemPrompt() {
        return false;
    }

    @Override
    public List<String> commandFor(AiRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.sessionId().isPresent() && !request.sessionId().get().isBlank()) {
            return List.of(binaryName, "--resume", request.sessionId().get(), "-p");
        }
        return List.of(binaryName, "-p");
    }

    @Override
    public CommandBackedRun askWithResult(AiRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.systemPrompt().isPresent() && !supportsSystemPrompt()) {
            throw new AiBackendUnsupportedRequestException(
                    backendId.value() + " backend does not support system prompts yet.", backendId);
        }

        List<String> command = commandFor(request);
        CommandResult result;
        try {
            result = commandExecutor.run(new CommandRequest(command, request.effectivePrompt(), timeout));
        } catch (CommandExecutionException exception) {
            throw new AiBackendStartupException(
                    "Could not start " + backendId.value() + ": " + exception.getMessage(), backendId, exception);
        }

        if (result.timedOut()) {
            throw new AiBackendExecutionException(backendId.value() + " timed out after " + timeout, backendId, result);
        }
        if (result.exitCode() != 0) {
            throw new AiBackendExecutionException(nonzeroExitMessage(result), backendId, result);
        }

        AiResponse response = new AiResponse(
                result.standardOutput(),
                backendId,
                result.duration()
        );
        return new CommandBackedRun(response, result, command);
    }

    private String nonzeroExitMessage(CommandResult result) {
        String message = backendId.value() + " exited with status " + result.exitCode();
        if (!result.standardError().isBlank()) {
            return message + ": " + result.standardError().strip();
        }
        return message;
    }
}
