package chatmap.backend.providers;

import java.util.Optional;

import chatmap.importer.ImportedChat;

/**
 * A live AI chat provider (Claude, ChatGPT, ...) that can hand back the user's
 * most recent conversation so it can be imported and summarized.
 *
 * This is the seam ChatMap uses to make "the last live chat from a provider"
 * the default target of {@code summarizeChat}. Implementations talk to the
 * network; callers treat a thrown exception or an empty result as "no live
 * chat available" and fall back to the most recent locally stored chat.
 */
public interface ChatProvider {

    /** Human-readable provider name, e.g. "Claude" or "ChatGPT". Used in log output. */
    String name();

    /**
     * The provider's most recent ("last live") conversation, normalized for
     * import, or empty when the provider has no conversations to offer.
     *
     * @throws Exception when the provider cannot be reached or its response
     *         cannot be parsed; callers treat this as "unavailable" and move on.
     */
    Optional<ImportedChat> latestChat() throws Exception;
}
