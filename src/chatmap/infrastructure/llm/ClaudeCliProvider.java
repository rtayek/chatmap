package chatmap.infrastructure.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.OutputFormat;
import chatmap.application.port.llm.PermissionMode;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandResult;

public final class ClaudeCliProvider extends CliLlmProvider {
    public ClaudeCliProvider(CommandExecutor commandExecutor, Duration timeout) {
        super(commandExecutor, timeout);
    }

    @Override
    public Set<LlmCapability> capabilities(ModelTarget target) {
        return Set.of(LlmCapability.sessions, LlmCapability.systemPrompt,
                LlmCapability.streamJson, LlmCapability.fileEditing);
    }

    @Override
    public List<String> commandFor(ModelTarget target, LlmRequest request) {
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
    protected LlmResponse parseResponse(ModelTarget target, LlmRequest request, CommandResult result) {
        StructuredCliOutput.Parsed parsed = StructuredCliOutput.parse(
                result.standardOutput(), request.sessionId().orElse(null));
        return new LlmResponse(parsed.text(), backendId(target), result.duration(), target, parsed.sessionId());
    }
}
