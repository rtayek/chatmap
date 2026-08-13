package chatmap.application.port.command;

public final class CommandExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CommandExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public CommandExecutionException(String message) {
        super(message);
    }
}
