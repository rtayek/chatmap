package chatmap.cli;

import java.nio.file.Path;
import java.sql.Connection;

import chatmap.service.ChatGptArchiveImportService;
import chatmap.service.ChatGptArchiveImportService.BulkImportResult;
import chatmap.service.ImportService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

/** Imports a ChatGPT export ZIP into the same database used by the JavaFX app. */
public final class ImportChatGptArchiveCli {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: importChatGptArchive <chatgpt-export.zip>");
            System.exit(1);
            return;
        }
        Path archive = Path.of(args[0]);
        Path dbPath = Path.of(System.getProperty("user.home"), ".chatmap", "chatmap.db");

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
