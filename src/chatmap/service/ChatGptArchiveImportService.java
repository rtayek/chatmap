package chatmap.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import chatmap.importer.ChatGptArchiveImporter;
import chatmap.importer.ChatGptArchiveImporter.ArchiveReadResult;
import chatmap.importer.ChatGptArchiveImporter.CodexInspection;
import chatmap.importer.ChatGptArchiveImporter.Failure;
import chatmap.importer.ChatGptArchiveImporter.ImportedConversation;

/** Persists every usable conversation from a ChatGPT export ZIP. */
public final class ChatGptArchiveImportService {

    private final ChatGptArchiveImporter archiveImporter;
    private final ImportService importService;

    public ChatGptArchiveImportService(ImportService importService) {
        this(new ChatGptArchiveImporter(), importService);
    }

    ChatGptArchiveImportService(ChatGptArchiveImporter archiveImporter, ImportService importService) {
        this.archiveImporter = archiveImporter;
        this.importService = importService;
    }

    public BulkImportResult importArchive(Path zipPath) throws IOException, SQLException {
        ArchiveReadResult read = archiveImporter.read(zipPath, Instant.now().toString());
        CodexInspection codex = archiveImporter.inspectCodex(zipPath);
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        List<Failure> failures = new ArrayList<>(read.failures());

        for (ImportedConversation conversation : read.conversations()) {
            try {
                ImportService.PersistResult persisted = importService.persist(conversation.importedChat());
                switch (persisted.outcome()) {
                    case inserted -> inserted++;
                    case updated -> updated++;
                    case unchanged -> unchanged++;
                }
            } catch (SQLException | RuntimeException e) {
                failures.add(new Failure(conversation.entryName(), conversation.externalConversationId(),
                        concise(e)));
            }
        }

        return new BulkImportResult(
                read.archivePath(),
                read.conversationEntries(),
                read.conversationsDiscovered(),
                inserted,
                updated,
                unchanged,
                read.skipped(),
                failures.size(),
                read.unsupportedContentParts(),
                read.unsupportedContentCategories(),
                failures,
                codex);
    }

    private static String concise(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.replaceAll("\\s+", " ").strip();
    }

    public record BulkImportResult(
            Path archivePath,
            List<String> conversationEntries,
            int conversationsDiscovered,
            int inserted,
            int updated,
            int unchanged,
            int skipped,
            int failed,
            int unsupportedContentParts,
            java.util.Map<String, Integer> unsupportedContentCategories,
            List<Failure> failures,
            CodexInspection codexInspection) {
        public BulkImportResult {
            conversationEntries = List.copyOf(conversationEntries);
            unsupportedContentCategories = java.util.Map.copyOf(unsupportedContentCategories);
            failures = List.copyOf(failures);
        }

        public int persisted() {
            return inserted + updated + unchanged;
        }

        public String summary() {
            return "ChatGPT archive: discovered " + conversationsDiscovered
                    + ", inserted " + inserted
                    + ", updated " + updated
                    + ", unchanged " + unchanged
                    + ", skipped " + skipped
                    + ", failed " + failed;
        }
    }
}
