package chatmap.cli;

import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.domain.ChatSummary;
import chatmap.service.LiveChatFetchService;

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

        System.out.println(ChatMapPaths.diagnostics(parsedArguments.paths()));

        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            LiveChatFetchService.Resolution resolution;
            try {
                resolution = context.liveChatFetchService().resolve(requestedChatId);
            } catch (LiveChatFetchService.NoChatAvailableException e) {
                System.err.println(e.getMessage());
                System.exit(1);
                return;
            }
            long chatId = resolution.chatId();
            if (requestedChatId == null) {
                System.out.println("No chatId given; defaulting to " + resolution.how() + ".");
            }

            System.out.println("Summarizing chat " + chatId + " ...");
            ChatSummary summary = context.summaryService().summarize(chatId);

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
