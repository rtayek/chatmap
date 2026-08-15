package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;
import chatmap.application.port.ai.CommandBackedAiBackend;
import chatmap.application.port.ai.CommandBackedRun;

import chatmap.application.port.command.CommandExecutor;

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
        return StandardCliBackend.execute(commandExecutor, backendId, timeout, request, commandFor(request));
    }

    @Override
    public List<String> commandFor(AiRequest request) {
        Objects.requireNonNull(request, "request");
        return List.of("ollama", "run", modelName);
    }
}
