package chatmap.backend;

public final class AiBackendStartupException extends AiBackendException {
    private static final long serialVersionUID = 1L;

    public AiBackendStartupException(String message, BackendId backendId, Throwable cause) {
        super(message, backendId, cause);
    }
}
