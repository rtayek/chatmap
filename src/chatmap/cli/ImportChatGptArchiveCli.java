package chatmap.cli;

import java.nio.file.Path;

import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.service.ChatGptArchiveImportService.BulkImportResult;

/** Imports a ChatGPT export ZIP into the same database used by the JavaFX app. */
public final class ImportChatGptArchiveCli {

    private static final String USAGE = "Usage: importChatGptArchive [--home <directory>] <chatgpt-export.zip>";

    public static void main(String[] args) {
        ParsedArguments parsedArguments = CliBootstrap.parseOrExit(args, USAGE);
        if (parsedArguments.remainingArgs().size() != 1) {
            CliBootstrap.exitWithUsage(USAGE);
            return;
        }
        Path archive = Path.of(parsedArguments.remainingArgs().getFirst());
        System.out.println(ChatMapPaths.diagnostics(parsedArguments.paths()));

        try (CliBootstrap.CliContext context = CliBootstrap.open(parsedArguments)) {
            BulkImportResult result = context.services().archiveImportService().importArchive(archive);
            System.out.println(result.summary());
            System.out.println("conversation entries: " + result.conversationEntries().size());
            System.out.println("unsupported content parts: " + result.unsupportedContentParts());
            if (!result.unsupportedContentCategories().isEmpty()) {
                System.out.println("unsupported content categories:");
                result.unsupportedContentCategories().entrySet().stream()
                        .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(entry -> System.out.println("- " + entry.getKey() + ": " + entry.getValue()));
            }
            System.out.println("codex.json: " + codexSummary(result));
            if (!result.failures().isEmpty()) {
                System.out.println("failures:");
                for (var failure : result.failures()) {
                    System.out.println("- " + failure.entryName() + " "
                            + (failure.conversationId() == null ? "" : failure.conversationId() + " ")
                            + failure.reason());
                }
            }
        } catch (Exception e) {
            System.err.println("Could not import ChatGPT archive: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String codexSummary(BulkImportResult result) {
        var codex = result.codexInspection();
        if (!codex.present()) {
            return "missing";
        }
        return codex.topLevelType() + " records=" + codex.recordCount()
                + " normalConversationSchema=" + codex.normalConversationSchema()
                + " codexTurnSchema=" + codex.codexTurnSchema();
    }
}
