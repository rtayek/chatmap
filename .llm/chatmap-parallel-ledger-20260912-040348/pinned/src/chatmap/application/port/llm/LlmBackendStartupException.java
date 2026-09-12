package chatmap.application.port.llm;

public final class LlmBackendStartupException extends LlmBackendException {
    private static final long serialVersionUID = 1L;

    public LlmBackendStartupException(String message, BackendId backendId, Throwable cause) {
        super(message, backendId, cause);
    }
}
