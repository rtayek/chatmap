package chatmap.application.port.handoff;

/** Wraps an unexpected filesystem failure from a {@link HandoffFileStore} operation. */
public final class HandoffFileStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HandoffFileStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
