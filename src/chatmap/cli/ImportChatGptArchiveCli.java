package chatmap.cli;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;

import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.config.ChatMapPaths.ResolvedPaths;
import chatmap.service.ChatGptArchiveImportService;
import chatmap.service.ChatGptArchiveImportService.BulkImportResult;
import chatmap.service.ImportService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

/** Imports a ChatGPT export ZIP into the same database used by the JavaFX app. */
public final class ImportChatGptArchiveCli {

    public static void main(String[] args) {
        ParsedArguments parsedArguments;
        try {
            parsedArguments = ChatMapPaths.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Usage: importChatGptArchive [--home <directory>] <chatgpt-export.zip>");
            System.exit(1);
            return;
        }
        if (parsedArguments.remainingArgs().size() != 1) {
            System.err.println("Usage: importChatGptArchive [--home <directory>] <chatgpt-export.zip>");
            System.exit(1);
            return;
        }
        Path archive = Path.of(parsedArguments.remainingArgs().getFirst());
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
            ImportService importService = new ImportService(chats, new MessageRepository(conn));
            ChatGptArchiveImportService archiveImportService =
                    new ChatGptArchiveImportService(importService, chats);
            BulkImportResult result = archiveImportService.importArchive(archive);
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
