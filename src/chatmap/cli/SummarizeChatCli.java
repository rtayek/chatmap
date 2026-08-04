package chatmap.cli;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.backend.ChatProvider;
import chatmap.backend.ClaudeCliClient;
import chatmap.backend.HttpChatProvider;
import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.importer.ImportedChat;
import chatmap.service.ImportService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.SummaryRepository;
import chatmap.storage.TagRepository;
import chatmap.service.SummaryService;

/**
 * Command-line entry point for the one deliberate extra step: summarize and
 * tag one already-imported chat, using the same backend that produced it.
 *
 * Uses the same database file as the JavaFX app (~/.chatmap/chatmap.db), so
 * it can summarize chats imported through either the app or the consolidator
 * CLI. Does not touch the import/export flow at all.
 *
 * Usage: summarizeChat [chatId]
 *
 * With no argument, the target is chosen in this order:
 *   1. the last live chat from a configured provider (imported on the fly), else
 *   2. the most recently imported chat already in the database.
 */
public final class SummarizeChatCli {

    /** Outcome of choosing which chat to summarize: its id plus how it was chosen (for logging). */
    record Resolution(long chatId, String how) {}

    public static void main(String[] args) {
        Long requestedChatId = null;
        if (args.length >= 1) {
            try {
                requestedChatId = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("chatId must be a number, got: " + args[0]);
                System.exit(1);
                return;
            }
        }

        Path dbPath = Path.of(System.getProperty("user.home"), ".chatmap", "chatmap.db");

        try (Connection conn = new Database("jdbc:sqlite:" + dbPath).openAndInitialize()) {
            ChatRepository chats = new ChatRepository(conn);
            MessageRepository messages = new MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages);

            List<ChatProvider> providers = new ArrayList<>();
            HttpChatProvider.fromEnv().ifPresent(providers::add);

            Resolution resolution;
            try {
                resolution = resolveChatId(requestedChatId, providers, chats, importService);
            } catch (NoChatAvailableException e) {
                System.err.println(e.getMessage());
                System.exit(1);
                return;
            }
            long chatId = resolution.chatId();
            if (requestedChatId == null) {
                System.out.println("No chatId given; defaulting to " + resolution.how() + ".");
            }

            SummaryService summaryService = new SummaryService(
                    chats,
                    messages,
                    new SummaryRepository(conn),
                    new TagRepository(conn),
                    new ClaudeCliClient(Duration.ofMinutes(3)));

            System.out.println("Summarizing chat " + chatId + " ...");
            ChatSummary summary = summaryService.summarize(chatId);

            System.out.println();
            System.out.println("Summary:");
            System.out.println(summary.summary());
        } catch (Exception e) {
            String which = requestedChatId != null ? String.valueOf(requestedChatId) : "(most recent)";
            System.err.println("Could not summarize chat " + which + ": " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Chooses which chat to summarize when no explicit chatId is given.
     *
     * Priority: an explicit {@code requestedChatId} wins; otherwise the last
     * live chat from the first available provider is imported and used; if no
     * provider yields one (or all fail), it falls back to the most recently
     * imported chat already stored locally.
     *
     * @throws NoChatAvailableException when no provider has a live chat and the
     *         local database is empty.
     */
    static Resolution resolveChatId(Long requestedChatId, List<ChatProvider> providers,
            ChatRepository chats, ImportService importService)
            throws SQLException, NoChatAvailableException {

        if (requestedChatId != null) {
            return new Resolution(requestedChatId, "requested chat " + requestedChatId);
        }

        for (ChatProvider provider : providers) {
            try {
                Optional<ImportedChat> live = provider.latestChat();
                if (live.isPresent()) {
                    Chat stored = importService.persist(live.get());
                    return new Resolution(stored.id(),
                            "last live chat from " + provider.name() + " (\"" + stored.title() + "\")");
                }
                System.err.println("Provider " + provider.name() + " has no live chat; trying next.");
            } catch (Exception e) {
                System.err.println("Provider " + provider.name() + " unavailable: " + e.getMessage());
            }
        }

        Optional<Chat> latest = mostRecentChat(chats);
        if (latest.isPresent()) {
            return new Resolution(latest.get().id(),
                    "most recent stored chat " + latest.get().id() + " (\"" + latest.get().title() + "\")");
        }
        throw new NoChatAvailableException(
                "No live provider chat and no stored chats; nothing to summarize.");
    }

    /**
     * The chat used as the local fallback: the most recently imported one.
     * {@link ChatRepository#findAll()} is ordered by importedAt then id, so the
     * last element is the most recent. Empty when there are no chats.
     */
    static Optional<Chat> mostRecentChat(ChatRepository chats) throws SQLException {
        List<Chat> all = chats.findAll();
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(all.get(all.size() - 1));
    }

    /** Raised when there is neither a live provider chat nor a stored chat to summarize. */
    static final class NoChatAvailableException extends Exception {
        NoChatAvailableException(String message) {
            super(message);
        }
    }
}
