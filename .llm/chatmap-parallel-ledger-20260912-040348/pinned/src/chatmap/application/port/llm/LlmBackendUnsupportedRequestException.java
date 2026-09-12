package chatmap.application.port.llm;

public final class LlmBackendUnsupportedRequestException extends LlmBackendException {
    private static final long serialVersionUID = 1L;

    public LlmBackendUnsupportedRequestException(String message, BackendId backendId) {
        super(message, backendId);
    }
}
