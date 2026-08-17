package chatmap.application.port.provider;

import java.util.Optional;
import java.util.List;

import chatmap.domain.ConversationCandidate;
import chatmap.application.model.ImportedChat;

/**
 * A live LLM chat provider (Claude, ChatGPT, ...) that can hand back the user's
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
     * @throws ChatProviderException when the provider cannot be reached or its
     *         response cannot be parsed; callers treat this as "unavailable"
     *         and move on.
     */
    Optional<ImportedChat> latestChat() throws ChatProviderException;

    /**
     * Metadata-only conversation discovery. Implementations should avoid reading
     * full transcripts here; failures are isolated by the inventory service.
     *
     * <p>Two signaling styles coexist here, both deliberate: local CLI-history
     * providers throw {@link ChatProviderException} on a genuine I/O failure
     * (e.g. session files unreadable), since there's nothing else useful to
     * report. Live web providers never throw from here -- an unreachable CDP
     * endpoint or an incomplete scroll returns an empty list instead, with the
     * reason surfaced through {@link #inventoryComplete()} and
     * {@link #inventoryDiagnostic()} rather than an exception, since those
     * already carry a richer "how much of the history did we actually see"
     * signal that a thrown exception would only replace with less
     * information. Callers that want to catch every unavailability case, not
     * just the local-CLI kind, must check both.</p>
     */
    default List<ConversationCandidate> listChats() throws ChatProviderException {
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
    default ImportedChat fetch(ConversationCandidate candidate) throws ChatProviderException {
        return latestChat().orElseThrow(() ->
                new NoImportableContentException("Provider has no fetchable chat for " + candidate));
    }

    /**
     * Whether {@link #listChats()} is known to cover the provider's complete
     * history. For live web providers this is also how an unreachable
     * endpoint surfaces (see {@link #listChats()}) -- {@code false} here plus
     * a reason in {@link #inventoryDiagnostic()} may mean "incomplete scroll"
     * or "couldn't connect at all," not just "more chats exist than we saw."
     */
    default boolean inventoryComplete() {
        return true;
    }

    /**
     * Concise provider-specific limitation or last unavailable reason for
     * inventory reports; read this alongside {@link #inventoryComplete()}
     * for the live web providers that signal unavailability this way instead
     * of throwing from {@link #listChats()}.
     */
    default Optional<String> inventoryDiagnostic() {
        return Optional.empty();
    }

    /** Last provider-specific reason a live transcript could not be read. */
    default Optional<String> unavailableReason() {
        return Optional.empty();
    }
}
