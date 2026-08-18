package chatmap.infrastructure.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.OutputFormat;
import chatmap.application.port.llm.PermissionMode;
import chatmap.application.port.command.CommandExecutor;

public final class AntigravityCliProvider extends CliLlmProvider {
    public AntigravityCliProvider(CommandExecutor commandExecutor, Duration timeout) {
        super(commandExecutor, timeout);
    }

    @Override
    public Set<LlmCapability> capabilities(ModelTarget target) {
        return Set.of(LlmCapability.sessions, LlmCapability.streamJson, LlmCapability.fileEditing);
    }

    @Override
    public List<String> commandFor(ModelTarget target, LlmRequest request) {
        List<String> command = new ArrayList<>();
        command.add("agy");
        request.sessionId().filter(id -> !id.isBlank()).ifPresent(id -> {
            command.add("--conversation");
            command.add(id);
        });
        if (request.permissionMode() == PermissionMode.unrestricted) {
            command.add("--dangerously-skip-permissions");
        }
        command.add("--output-format");
        command.add(request.outputFormat() == OutputFormat.streamJson ? "stream-json" : "json");
        command.add("--print");
        command.add(request.effectivePrompt());
        return command;
    }


}


