package chatmap.cli;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import chatmap.backend.ChatProvider;
import chatmap.backend.ClaudeCliClient;
import chatmap.backend.ClaudeWebChatProvider;
import chatmap.domain.ChatSummary;
import chatmap.service.ImportService;
import chatmap.service.LiveChatFetchService;
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
            providers.add(new ClaudeWebChatProvider());
            LiveChatFetchService fetchService =
                    new LiveChatFetchService(providers, importService, chats);

            LiveChatFetchService.Resolution resolution;
            try {
                resolution = fetchService.resolve(requestedChatId);
            } catch (LiveChatFetchService.NoChatAvailableException e) {
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
}
