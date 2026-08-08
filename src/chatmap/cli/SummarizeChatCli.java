package chatmap.cli;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;

import chatmap.backend.ChatProvider;
import chatmap.backend.ClaudeCliBackend;
import chatmap.backend.DefaultChatProviders;
import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.config.ChatMapPaths.ResolvedPaths;
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
 * tag one already-imported chat using the configured Claude CLI client.
 *
 * Uses the same configured database file as the JavaFX app, so
 * it can summarize chats imported through either the app or the consolidator
 * CLI. Does not touch the import/export flow at all.
 *
 * Usage: summarizeChat [--home <directory>] [chatId]
 *
 * With no argument, the target is chosen in this order:
 *   1. the last live chat from a configured provider (imported on the fly), else
 *   2. the most recently imported chat already in the database.
 */
public final class SummarizeChatCli {

    public static void main(String[] args) {
        ParsedArguments parsedArguments;
        try {
            parsedArguments = ChatMapPaths.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Usage: summarizeChat [--home <directory>] [chatId]");
            System.exit(1);
            return;
        }
        if (parsedArguments.remainingArgs().size() > 1) {
            System.err.println("Usage: summarizeChat [--home <directory>] [chatId]");
            System.exit(1);
            return;
        }
        Long requestedChatId = null;
        if (!parsedArguments.remainingArgs().isEmpty()) {
            try {
                requestedChatId = Long.parseLong(parsedArguments.remainingArgs().getFirst());
            } catch (NumberFormatException e) {
                System.err.println("chatId must be a number, got: " + parsedArguments.remainingArgs().getFirst());
                System.exit(1);
                return;
            }
        }

        ResolvedPaths paths = parsedArguments.paths();
        Path dbPath = paths.databasePath();
        System.out.println(ChatMapPaths.diagnostics(paths));

        try {
            Files.createDirectories(paths.homeDirectory());
        } catch (Exception e) {
            System.err.println("Could not create ChatMap data directory: " + e.getMessage());
            System.exit(1);
            return;
        }

        try (Connection conn = new Database("jdbc:sqlite:" + dbPath).openAndInitialize()) {
            ChatRepository chats = new ChatRepository(conn);
            MessageRepository messages = new MessageRepository(conn);
            ImportService importService = new ImportService(chats, messages);

            List<ChatProvider> providers = DefaultChatProviders.ordered();
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
                    new ClaudeCliBackend(Duration.ofMinutes(3)));

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
