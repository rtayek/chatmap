package chatmap.infrastructure.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import chatmap.application.port.ai.AiCapability;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.PermissionMode;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandResult;

public final class CodexCliProvider extends CliAiProvider {
    public CodexCliProvider(CommandExecutor commandExecutor, Duration timeout) {
        super(commandExecutor, timeout);
    }

    @Override
    public Set<AiCapability> capabilities(ModelTarget target) {
        return Set.of(AiCapability.sessions, AiCapability.fileEditing);
    }

    @Override
    public List<String> commandFor(ModelTarget target, AiRequest request) {
        List<String> command = new ArrayList<>();
        command.add(executableName(System.getProperty("os.name")));
        command.add("exec");
        command.add("--json");
        request.sessionId().filter(id -> !id.isBlank()).ifPresent(id -> {
            command.add("resume");
            command.add(id);
        });
        if (!"default".equals(target.providerModelName())) {
            command.add("--model");
            command.add(target.providerModelName());
        }
        if (request.permissionMode() == PermissionMode.unrestricted) {
            command.add("--sandbox");
            command.add("workspace-write");
        }
        command.add("-");
        return command;
    }

    @Override
    protected AiResponse parseResponse(ModelTarget target, AiRequest request, CommandResult result) {
        StructuredCliOutput.Parsed parsed = StructuredCliOutput.parse(
                result.standardOutput(), request.sessionId().orElse(null));
        return new AiResponse(parsed.text(), backendId(target), result.duration(), target, parsed.sessionId());
    }

    static String executableName(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        return normalized.contains("win") ? "codex.cmd" : "codex";
    }
}
