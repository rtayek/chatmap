package chatmap.application.port.llm;

import chatmap.application.port.command.CommandResult;

import java.util.Objects;
import java.util.Optional;

public final class LlmBackendExecutionException extends LlmBackendException {
    private static final long serialVersionUID = 1L;

    private final CommandResult commandResult;

    public LlmBackendExecutionException(String message, BackendId backendId, CommandResult commandResult) {
        super(message, backendId);
        this.commandResult = Objects.requireNonNull(commandResult, "commandResult");
    }

    @Override
    public Optional<CommandResult> commandResult() {
        return Optional.of(commandResult);
    }
}
