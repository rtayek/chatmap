package chatmap.application.port.ai;

public final class AiBackendUnsupportedRequestException extends AiBackendException {
    private static final long serialVersionUID = 1L;

    public AiBackendUnsupportedRequestException(String message, BackendId backendId) {
        super(message, backendId);
    }
}
