package chatmap.infrastructure.ai;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import chatmap.application.port.ai.AiBackendExecutionException;
import chatmap.application.port.ai.AiBackendStartupException;
import chatmap.application.port.ai.AiBackendUnsupportedRequestException;
import chatmap.application.port.ai.AiCapability;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;
import chatmap.application.port.ai.CommandBackedAiProvider;
import chatmap.application.port.ai.CommandBackedRun;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.command.CommandExecutionException;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;

/** Shared process execution for CLI providers; command syntax stays provider-specific. */
public abstract class CliAiProvider implements CommandBackedAiProvider {
    private final CommandExecutor commandExecutor;
    private final Duration timeout;

    protected CliAiProvider(CommandExecutor commandExecutor, Duration timeout) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public final AiResponse execute(ModelTarget target, AiRequest request) {
        return executeWithResult(target, request).response();
    }

    @Override
    public final CommandBackedRun executeWithResult(ModelTarget target, AiRequest request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(request, "request");
        validateCapabilities(target, request);
        var command = commandFor(target, request);
        CommandResult result;
        try {
            result = commandExecutor.run(new CommandRequest(
                    command, request.effectivePrompt(), timeout, request.workingDirectory().orElse(null)));
        } catch (CommandExecutionException exception) {
            throw new AiBackendStartupException(
                    "Could not start " + target.displayName() + ": " + exception.getMessage(),
                    backendId(target), exception);
        }
        if (result.timedOut()) {
            throw new AiBackendExecutionException(
                    target.displayName() + " timed out after " + timeout, backendId(target), result);
        }
        if (result.exitCode() != 0) {
            throw new AiBackendExecutionException(nonzeroExitMessage(target, result), backendId(target), result);
        }
        return new CommandBackedRun(parseResponse(target, request, result), result, command);
    }

    protected AiResponse parseResponse(ModelTarget target, AiRequest request, CommandResult result) {
        return new AiResponse(result.standardOutput(), backendId(target), result.duration(), target,
                request.sessionId().orElse(null));
    }

    protected final BackendId backendId(ModelTarget target) {
        return new BackendId(target.displayName());
    }

    protected final void validateCapabilities(ModelTarget target, AiRequest request) {
        Set<AiCapability> supported = capabilities(target);
        if (request.systemPrompt().isPresent() && !supported.contains(AiCapability.systemPrompt)) {
            throw unsupported(target, AiCapability.systemPrompt);
        }
        if (request.sessionId().isPresent() && !supported.contains(AiCapability.sessions)) {
            throw unsupported(target, AiCapability.sessions);
        }
        if (request.permissionMode() == chatmap.application.port.ai.PermissionMode.unrestricted
                && !supported.contains(AiCapability.fileEditing)) {
            throw unsupported(target, AiCapability.fileEditing);
        }
        if (request.outputFormat() == chatmap.application.port.ai.OutputFormat.streamJson
                && !supported.contains(AiCapability.streamJson)) {
            throw unsupported(target, AiCapability.streamJson);
        }
    }

    private AiBackendUnsupportedRequestException unsupported(ModelTarget target, AiCapability capability) {
        return new AiBackendUnsupportedRequestException(
                target.displayName() + " does not support requested capability: " + capability,
                backendId(target));
    }

    private String nonzeroExitMessage(ModelTarget target, CommandResult result) {
        String message = target.displayName() + " exited with status " + result.exitCode();
        if (!result.standardError().isBlank()) {
            return message + ": " + result.standardError().strip();
        }
        return message;
    }
}
