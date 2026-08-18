package chatmap.infrastructure.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import chatmap.application.port.llm.LlmBackendExecutionException;
import chatmap.application.port.llm.LlmBackendStartupException;
import chatmap.application.port.llm.LlmBackendUnsupportedRequestException;
import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.CommandBackedLlmProvider;
import chatmap.application.port.llm.CommandBackedRun;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.command.CommandExecutionException;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;

/** Shared process execution for CLI providers; command syntax stays provider-specific. */
public abstract class CliLlmProvider implements CommandBackedLlmProvider {
    private final CommandExecutor commandExecutor;
    private final Duration timeout;

    protected CliLlmProvider(CommandExecutor commandExecutor, Duration timeout) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public final LlmResponse execute(ModelTarget target, LlmRequest request) {
        return executeWithResult(target, request).response();
    }

    @Override
    public final CommandBackedRun executeWithResult(ModelTarget target, LlmRequest request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(request, "request");
        validateCapabilities(target, request);
        var command = commandFor(target, request);
        CommandResult result;
        try {
            result = commandExecutor.run(new CommandRequest(
                    command, request.effectivePrompt(), timeout, request.workingDirectory().orElse(null))
                    .withOutputPaths(request.standardOutputPath().orElse(null),
                            request.standardErrorPath().orElse(null)));
        } catch (CommandExecutionException exception) {
            throw new LlmBackendStartupException(
                    "Could not start " + target.displayName() + ": " + exception.getMessage(),
                    backendId(target), exception);
        }
        if (result.timedOut()) {
            throw new LlmBackendExecutionException(
                    target.displayName() + " timed out after " + timeout, backendId(target), result);
        }
        if (result.exitCode() != 0) {
            throw new LlmBackendExecutionException(nonzeroExitMessage(target, result), backendId(target), result);
        }
        return new CommandBackedRun(parseResponse(target, request, result), result, command);
    }

    protected LlmResponse parseResponse(ModelTarget target, LlmRequest request, CommandResult result) {
        try {
            StructuredCliOutput.Parsed parsed = StructuredCliOutput.parse(
                    result.standardOutput(), request.sessionId().orElse(null));
            return new LlmResponse(parsed.text(), backendId(target), result.duration(), target, parsed.sessionId());
        } catch (StructuredOutputException e) {
            throw new LlmBackendExecutionException(e.getMessage(), backendId(target), result);
        }
    }

    protected final BackendId backendId(ModelTarget target) {
        return new BackendId(target.displayName());
    }

    protected final void validateCapabilities(ModelTarget target, LlmRequest request) {
        Set<LlmCapability> supported = capabilities(target);
        if (request.systemPrompt().isPresent() && !supported.contains(LlmCapability.systemPrompt)) {
            throw unsupported(target, LlmCapability.systemPrompt);
        }
        if (request.sessionId().isPresent() && !supported.contains(LlmCapability.sessions)) {
            throw unsupported(target, LlmCapability.sessions);
        }
        if (request.permissionMode() == chatmap.application.port.llm.PermissionMode.unrestricted
                && !supported.contains(LlmCapability.fileEditing)) {
            throw unsupported(target, LlmCapability.fileEditing);
        }
        if (request.outputFormat() == chatmap.application.port.llm.OutputFormat.streamJson
                && !supported.contains(LlmCapability.streamJson)) {
            throw unsupported(target, LlmCapability.streamJson);
        }
    }

    private LlmBackendUnsupportedRequestException unsupported(ModelTarget target, LlmCapability capability) {
        return new LlmBackendUnsupportedRequestException(
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
