package chatmap.application.port.llm;

import chatmap.application.port.command.CommandResult;

import java.util.Objects;
import java.util.Optional;

public abstract sealed class LlmBackendException extends RuntimeException
        permits LlmBackendStartupException, LlmBackendExecutionException, LlmBackendUnsupportedRequestException {
    private static final long serialVersionUID = 1L;

    private final BackendId backendId;

    LlmBackendException(String message, BackendId backendId) {
        super(message);
        this.backendId = Objects.requireNonNull(backendId, "backendId");
    }

    LlmBackendException(String message, BackendId backendId, Throwable cause) {
        super(message, cause);
        this.backendId = Objects.requireNonNull(backendId, "backendId");
    }

    public BackendId backendId() {
        return backendId;
    }

    public Optional<CommandResult> commandResult() {
        return Optional.empty();
    }
}
