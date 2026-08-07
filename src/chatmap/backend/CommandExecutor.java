package chatmap.backend;

public interface CommandExecutor {
    CommandResult run(CommandRequest request);
}
