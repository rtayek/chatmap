package chatmap.backend.providers;

/**
 * A {@link ChatProvider#fetch} candidate resolved to zero real turns -- e.g. a
 * Codex rollout file for an abandoned session, or a Gemini CLI session file
 * that contains only injected setup context and no user turn. This is not a
 * failure: the candidate was read successfully, it just has nothing worth
 * importing. Callers that tally results (e.g. {@code ImportAllChatsCli})
 * should count this separately from a genuine {@link ChatProviderException}
 * or other unexpected error.
 */
public class NoImportableContentException extends RuntimeException {

    public NoImportableContentException(String message) {
        super(message);
    }
}
