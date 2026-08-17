package chatmap.infrastructure.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import chatmap.application.port.ai.AiCapability;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.OutputFormat;
import chatmap.application.port.ai.PermissionMode;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandResult;

public final class ClaudeCliProvider extends CliAiProvider {
    public ClaudeCliProvider(CommandExecutor commandExecutor, Duration timeout) {
        super(commandExecutor, timeout);
    }

    @Override
    public Set<AiCapability> capabilities(ModelTarget target) {
        return Set.of(AiCapability.sessions, AiCapability.systemPrompt,
                AiCapability.streamJson, AiCapability.fileEditing);
    }

    @Override
    public List<String> commandFor(ModelTarget target, AiRequest request) {
        List<String> command = new ArrayList<>();
        command.add("claude");
        request.sessionId().filter(id -> !id.isBlank()).ifPresent(id -> {
            command.add("--resume");
            command.add(id);
        });
        command.add("-p");
        if (request.systemPrompt().isPresent()) {
            command.add("--system-prompt");
            command.add(request.systemPrompt().get());
        }
        if (request.permissionMode() == PermissionMode.unrestricted) {
            command.add("--dangerously-skip-permissions");
        }
        command.add("--output-format");
        if (request.outputFormat() == OutputFormat.streamJson) {
            command.add("stream-json");
            command.add("--verbose");
        } else {
            command.add("json");
        }
        return command;
    }

    @Override
    protected AiResponse parseResponse(ModelTarget target, AiRequest request, CommandResult result) {
        StructuredCliOutput.Parsed parsed = StructuredCliOutput.parse(
                result.standardOutput(), request.sessionId().orElse(null));
        return new AiResponse(parsed.text(), backendId(target), result.duration(), target, parsed.sessionId());
    }
}
