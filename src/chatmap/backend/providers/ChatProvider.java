package chatmap.backend.providers;

import java.util.Optional;
import java.util.List;

import chatmap.domain.ConversationCandidate;
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

    /**
     * Metadata-only conversation discovery. Implementations should avoid reading
     * full transcripts here; failures are isolated by the inventory service.
     */
    default List<ConversationCandidate> listChats() throws Exception {
        return latestChat()
                .map(chat -> List.of(new ConversationCandidate(
                        chat.chat().source(),
                        chat.chat().externalConversationId(),
                        chat.chat().title(),
                        chat.chat().sourceUri(),
                        chat.chat().sourceUpdatedAt())))
                .orElseGet(List::of);
    }

    /** Fetches one discovered conversation as an importable transcript. */
    default ImportedChat fetch(ConversationCandidate candidate) throws Exception {
        return latestChat().orElseThrow(() ->
                new IllegalArgumentException("Provider has no fetchable chat for " + candidate));
    }

    /** Whether {@link #listChats()} is known to cover the provider's complete history. */
    default boolean inventoryComplete() {
        return true;
    }

    /** Concise provider-specific limitation or last unavailable reason for inventory reports. */
    default Optional<String> inventoryDiagnostic() {
        return Optional.empty();
    }
}
