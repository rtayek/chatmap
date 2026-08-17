package chatmap.infrastructure.llm;

import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.ModelTarget;

import chatmap.application.port.command.CommandExecutor;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public final class OllamaCliProvider extends CliLlmProvider {
    public OllamaCliProvider(CommandExecutor commandExecutor, Duration timeout) {
        super(commandExecutor, timeout);
    }

    @Override
    public Set<LlmCapability> capabilities(ModelTarget target) {
        return Set.of();
    }

    @Override
    public List<String> commandFor(ModelTarget target, LlmRequest request) {
        return List.of("ollama", "run", target.providerModelName());
    }
}
