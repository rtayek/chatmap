package chatmap.backend.providers;

/**
 * A {@link ChatProvider} could not complete a request: its endpoint or session
 * was unreachable, its process could not be started, its response could not
 * be parsed, etc. Callers that want to distinguish "this provider is
 * unavailable" from an unrelated bug should catch this rather than a bare
 * {@code Exception}.
 */
public class ChatProviderException extends Exception {

    public ChatProviderException(String message) {
        super(message);
    }

    public ChatProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
