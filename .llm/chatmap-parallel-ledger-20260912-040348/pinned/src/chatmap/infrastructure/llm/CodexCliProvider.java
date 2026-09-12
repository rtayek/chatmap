package chatmap.infrastructure.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import java.util.Set;

import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.PermissionMode;
import chatmap.application.port.command.CommandExecutor;

public final class CodexCliProvider extends CliLlmProvider {
    public CodexCliProvider(CommandExecutor commandExecutor, Duration timeout) {
        super(commandExecutor, timeout);
    }

    @Override
    public Set<LlmCapability> capabilities(ModelTarget target) {
        return Set.of(LlmCapability.sessions, LlmCapability.fileEditing);
    }

    @Override
    public List<String> commandFor(ModelTarget target, LlmRequest request) {
        List<String> command = new ArrayList<>();
        command.add(executableName(System.getProperty("os.name")));
        command.add("exec");
        command.add("--json");
        request.sessionId().filter(id -> !id.isBlank()).ifPresent(id -> {
            command.add("resume");
            command.add(id);
        });
        target.providerModelName().ifPresent(model -> {
            command.add("--model");
            command.add(model);
        });
        if (request.permissionMode() == PermissionMode.unrestricted) {
            command.add("--sandbox");
            command.add("workspace-write");
        }
        command.add("-");
        return command;
    }


    static String executableName(String osName) {
        return "codex";
    }
}


