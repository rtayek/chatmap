package chatmap.backend.ai;

import chatmap.backend.command.CommandExecutionException;

import chatmap.backend.command.CommandResult;

import chatmap.backend.command.CommandRequest;

import chatmap.backend.command.CommandExecutor;

import chatmap.domain.Source;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class OllamaCliBackend implements CommandBackedAiBackend {
    private final CommandExecutor commandExecutor;
    private final Duration timeout;
    private final String modelName;
    private final BackendId backendId;

    public OllamaCliBackend(CommandExecutor commandExecutor, Duration timeout, String modelName) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        this.backendId = new BackendId("Ollama " + modelName);
    }

    @Override
    public AiResponse ask(AiRequest request) {
        return askWithResult(request).response();
    }

    @Override
    public Source source() {
        return Source.ollamaPrompt;
    }

    @Override
    public CommandBackedRun askWithResult(AiRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.systemPrompt().isPresent()) {
            throw new AiBackendUnsupportedRequestException(
                    backendId + " does not support system prompts yet.", backendId);
        }

        List<String> command = commandFor(request);
        CommandResult result;
        try {
            result = commandExecutor.run(new CommandRequest(command, request.effectivePrompt(), timeout));
        } catch (CommandExecutionException exception) {
            throw new AiBackendStartupException(
                    "Could not start " + backendId + ": " + exception.getMessage(), backendId, exception);
        }

        if (result.timedOut()) {
            throw new AiBackendExecutionException(backendId + " timed out after " + timeout, backendId, result);
        }
        if (result.exitCode() != 0) {
            throw new AiBackendExecutionException(nonzeroExitMessage(result), backendId, result);
        }

        AiResponse response = new AiResponse(result.standardOutput(), backendId, result.duration());
        return new CommandBackedRun(response, result, command);
    }

    @Override
    public List<String> commandFor(AiRequest request) {
        Objects.requireNonNull(request, "request");
        return List.of("ollama", "run", modelName);
    }

    private String nonzeroExitMessage(CommandResult result) {
        String message = backendId + " exited with status " + result.exitCode();
        if (!result.standardError().isBlank()) {
            return message + ": " + result.standardError().strip();
        }
        return message;
    }
}
