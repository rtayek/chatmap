package chatmap.cli;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;

import chatmap.backend.ClaudeCliClient;
import chatmap.domain.ChatSummary;
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
 * Usage: summarizeChat <chatId>
 */
public final class SummarizeChatCli {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: summarizeChat <chatId>");
            System.exit(1);
        }

        long chatId;
        try {
            chatId = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("chatId must be a number, got: " + args[0]);
            System.exit(1);
            return;
        }

        Path dbPath = Path.of(System.getProperty("user.home"), ".chatmap", "chatmap.db");

        try (Connection conn = new Database("jdbc:sqlite:" + dbPath).openAndInitialize()) {
            SummaryService summaryService = new SummaryService(
                    new ChatRepository(conn),
                    new MessageRepository(conn),
                    new SummaryRepository(conn),
                    new TagRepository(conn),
                    new ClaudeCliClient(Duration.ofMinutes(3)));

            System.out.println("Summarizing chat " + chatId + " ...");
            ChatSummary summary = summaryService.summarize(chatId);

            System.out.println();
            System.out.println("Summary:");
            System.out.println(summary.summary());
        } catch (Exception e) {
            System.err.println("Could not summarize chat " + chatId + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
