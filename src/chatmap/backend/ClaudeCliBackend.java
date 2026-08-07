package chatmap.backend;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class ClaudeCliBackend implements CommandBackedAiBackend {
    static final BackendId BACKEND_ID = new BackendId("Claude CLI");

    private final CommandExecutor commandExecutor;
    private final Duration timeout;

    public ClaudeCliBackend(CommandExecutor commandExecutor, Duration timeout) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public AiResponse ask(AiRequest request) {
        return askWithResult(request).response();
    }

    boolean supportsSystemPrompt() {
        return false;
    }

    @Override
    public CommandBackedRun askWithResult(AiRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.systemPrompt().isPresent() && !supportsSystemPrompt()) {
            throw new AiBackendUnsupportedRequestException(
                    "Claude CLI backend does not support system prompts yet.", BACKEND_ID);
        }

        List<String> command = commandFor(request);
        CommandResult result;
        try {
            result = commandExecutor.run(new CommandRequest(command, request.effectivePrompt(), timeout));
        } catch (CommandExecutionException exception) {
            throw new AiBackendStartupException(
                    "Could not start Claude CLI: " + exception.getMessage(), BACKEND_ID, exception);
        }

        if (result.timedOut()) {
            throw new AiBackendExecutionException("Claude CLI timed out after " + timeout, BACKEND_ID, result);
        }
        if (result.exitCode() != 0) {
            throw new AiBackendExecutionException(nonzeroExitMessage(result), BACKEND_ID, result);
        }

        AiResponse response = new AiResponse(
                result.standardOutput(),
                BACKEND_ID,
                result.duration()
        );
        return new CommandBackedRun(response, result, command);
    }

    private static String nonzeroExitMessage(CommandResult result) {
        String message = "Claude CLI exited with status " + result.exitCode();
        if (!result.standardError().isBlank()) {
            return message + ": " + result.standardError().strip();
        }
        return message;
    }

    @Override
    public List<String> commandFor(AiRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.sessionId().isPresent() && !request.sessionId().get().isBlank()) {
            return List.of("claude", "--resume", request.sessionId().get(), "-p");
        }
        return List.of("claude", "-p");
    }
}
