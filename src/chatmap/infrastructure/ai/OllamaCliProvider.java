package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiCapability;
import chatmap.application.port.ai.ModelTarget;

import chatmap.application.port.command.CommandExecutor;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public final class OllamaCliProvider extends CliAiProvider {
    public OllamaCliProvider(CommandExecutor commandExecutor, Duration timeout) {
        super(commandExecutor, timeout);
    }

    @Override
    public Set<AiCapability> capabilities(ModelTarget target) {
        return Set.of();
    }

    @Override
    public List<String> commandFor(ModelTarget target, AiRequest request) {
        return List.of("ollama", "run", target.providerModelName());
    }
}
