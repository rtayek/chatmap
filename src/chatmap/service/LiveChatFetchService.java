package chatmap.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import chatmap.backend.providers.ChatProvider;
import chatmap.domain.Chat;
import chatmap.importer.ImportedChat;
import chatmap.storage.ChatRepository;
import chatmap.util.Log;

/**
 * Chooses which chat to act on, shared by the CLI and the JavaFX app so the two
 * never drift apart.
 *
 * Fallback order (the single source of truth for it):
 * <ol>
 *   <li>an explicit chatId, when given, wins;</li>
 *   <li>otherwise the last live chat from the first available provider is
 *       imported on the fly and used;</li>
 *   <li>otherwise the most recently imported chat already stored locally;</li>
 *   <li>otherwise {@link NoChatAvailableException}.</li>
 * </ol>
 *
 * A provider that returns empty or throws is treated as unavailable and the next
 * option is tried; provider progress/failures are logged so both front ends
 * (whichever has a visible console) surface the same messages.
 */
public final class LiveChatFetchService {

    private static final Logger LOG = Log.of(LiveChatFetchService.class);

    /** Which chat was chosen, plus a human-readable note on how (for status/logging). */
    public record Resolution(long chatId, String how) {}

    /** Raised when there is neither a live provider chat nor a stored chat to act on. */
    public static final class NoChatAvailableException extends Exception {
        public NoChatAvailableException(String message) {
            super(message);
        }
    }

    private final List<ChatProvider> providers;
    private final ImportService importService;
    private final ChatRepository chats;

    public LiveChatFetchService(List<ChatProvider> providers, ImportService importService,
            ChatRepository chats) {
        this.providers = List.copyOf(providers);
        this.importService = importService;
        this.chats = chats;
    }

    public Resolution resolve(Long requestedChatId) throws SQLException, NoChatAvailableException {
        if (requestedChatId != null) {
            return new Resolution(requestedChatId, "requested chat " + requestedChatId);
        }

        for (ChatProvider provider : providers) {
            try {
                Optional<ImportedChat> live = provider.latestChat();
                if (live.isPresent()) {
                    Chat stored = importService.persist(live.get()).chat();
                    return new Resolution(stored.id(),
                            "last live chat from " + provider.name() + " (\"" + stored.title() + "\")");
                }
                LOG.info("Provider " + provider.name() + " has no live chat; trying next.");
            } catch (Exception e) {
                LOG.warning("Provider " + provider.name() + " unavailable: " + e.getMessage());
            }
        }

        Optional<Chat> latest = mostRecentChat();
        if (latest.isPresent()) {
            return new Resolution(latest.get().id(),
                    "most recent stored chat " + latest.get().id() + " (\"" + latest.get().title() + "\")");
        }
        throw new NoChatAvailableException(
                "No live provider chat and no stored chats; nothing to act on.");
    }

    /**
     * The local fallback: the most recently imported chat.
     * Delegates to {@link ChatRepository#findMostRecent()} for an O(1) query.
     */
    public Optional<Chat> mostRecentChat() throws SQLException {
        return chats.findMostRecent();
    }
}
