package chatmap.application.port.command;

public interface CommandExecutor {
    CommandResult run(CommandRequest request);
}
