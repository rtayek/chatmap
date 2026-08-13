package chatmap.application.port.ai;

import chatmap.application.port.command.CommandResult;

import java.util.Objects;
import java.util.Optional;

public final class AiBackendExecutionException extends AiBackendException {
    private static final long serialVersionUID = 1L;

    private final CommandResult commandResult;

    public AiBackendExecutionException(String message, BackendId backendId, CommandResult commandResult) {
        super(message, backendId);
        this.commandResult = Objects.requireNonNull(commandResult, "commandResult");
    }

    @Override
    public Optional<CommandResult> commandResult() {
        return Optional.of(commandResult);
    }
}
