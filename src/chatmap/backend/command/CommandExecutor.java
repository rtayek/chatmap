package chatmap.backend.command;

public interface CommandExecutor {
    CommandResult run(CommandRequest request);
}
