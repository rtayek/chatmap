package chatmap.infrastructure.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import chatmap.application.port.ai.AiBackendExecutionException;
import chatmap.application.port.ai.AiBackendStartupException;
import chatmap.application.port.ai.AiCapability;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.OutputFormat;
import chatmap.application.port.ai.PermissionMode;
import chatmap.application.port.command.CommandExecutionException;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;

public final class ClaudeCliProvider extends CliAiProvider {
    private static final Duration LIST_SESSIONS_TIMEOUT = Duration.ofSeconds(10);
    private final CommandExecutor commandExecutor;

    public ClaudeCliProvider(CommandExecutor commandExecutor, Duration timeout) {
        super(commandExecutor, timeout);
        this.commandExecutor = commandExecutor;
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
        if (request.outputFormat() == OutputFormat.streamJson) {
            command.add("--output-format");
            command.add("stream-json");
            command.add("--verbose");
        }
        return command;
    }

    @Override
    protected AiResponse parseResponse(ModelTarget target, AiRequest request, CommandResult result) {
        return new AiResponse(result.standardOutput(), backendId(target), result.duration(), target,
                extractSessionId(result.standardOutput(), request));
    }

    @Override
    public List<String> listSessions(ModelTarget target) {
        CommandResult result;
        try {
            result = commandExecutor.run(new CommandRequest(
                    List.of("claude", "--list-sessions"), "", LIST_SESSIONS_TIMEOUT));
        } catch (CommandExecutionException exception) {
            throw new AiBackendStartupException(
                    "Could not list sessions for " + target.displayName() + ": " + exception.getMessage(),
                    backendId(target), exception);
        }
        if (result.exitCode() != 0) {
            throw new AiBackendExecutionException(
                    target.displayName() + " --list-sessions exited with status " + result.exitCode()
                            + (result.standardError().isBlank() ? "" : ": " + result.standardError().strip()),
                    backendId(target), result);
        }
        if (result.standardOutput().isBlank()) {
            return List.of();
        }
        return result.standardOutput().lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .distinct()
                .toList();
    }

    private static String extractSessionId(String standardOutput, AiRequest request) {
        String requestedSession = request.sessionId().orElse(null);
        for (String line : standardOutput.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
                continue;
            }
            try {
                JsonObject object = JsonParser.parseString(trimmed).getAsJsonObject();
                if (object.has("session_id") && !object.get("session_id").isJsonNull()) {
                    String sessionId = object.get("session_id").getAsString();
                    if (!sessionId.isBlank()) {
                        return sessionId;
                    }
                }
            } catch (IllegalStateException | JsonParseException malformedJsonLine) {
                // stdout can contain non-JSON diagnostics mixed with JSONL; keep the requested
                // session id when no parseable provider-created id is present.
                continue;
            }
        }
        return requestedSession;
    }
}
