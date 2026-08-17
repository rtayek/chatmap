package chatmap.application.port.llm;

import chatmap.application.port.command.CommandResult;

import java.util.List;
import java.util.Objects;

public record CommandBackedRun(
        LlmResponse response,
        CommandResult commandResult,
        List<String> command
) {
    public CommandBackedRun {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(commandResult, "commandResult");
        command = List.copyOf(Objects.requireNonNull(command, "command"));
    }
}
