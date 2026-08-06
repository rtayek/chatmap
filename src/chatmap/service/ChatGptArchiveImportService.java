package chatmap.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import chatmap.domain.Chat;
import chatmap.domain.Source;
import chatmap.importer.ChatGptArchiveImporter;
import chatmap.importer.ChatGptArchiveImporter.ArchiveReadResult;
import chatmap.importer.ChatGptArchiveImporter.CodexInspection;
import chatmap.importer.ChatGptArchiveImporter.Failure;
import chatmap.importer.ChatGptArchiveImporter.ImportedConversation;
import chatmap.storage.ChatRepository;

/** Persists every usable conversation from a ChatGPT export ZIP. */
public final class ChatGptArchiveImportService {

    public enum Outcome {
        inserted,
        updated,
        unchanged
    }

    private final ChatGptArchiveImporter archiveImporter;
    private final ImportService importService;
    private final ChatRepository chats;

    public ChatGptArchiveImportService(ImportService importService, ChatRepository chats) {
        this(new ChatGptArchiveImporter(), importService, chats);
    }

    ChatGptArchiveImportService(ChatGptArchiveImporter archiveImporter, ImportService importService,
            ChatRepository chats) {
        this.archiveImporter = archiveImporter;
        this.importService = importService;
        this.chats = chats;
    }

    public BulkImportResult importArchive(Path zipPath) throws IOException, SQLException {
        ArchiveReadResult read = archiveImporter.read(zipPath, Instant.now().toString());
        CodexInspection codex = archiveImporter.inspectCodex(zipPath);
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        List<Failure> failures = new ArrayList<>(read.failures());

        for (ImportedConversation conversation : read.conversations()) {
            Outcome expected = expectedOutcome(conversation);
            try {
                importService.persist(conversation.importedChat());
                switch (expected) {
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

    private Outcome expectedOutcome(ImportedConversation conversation) throws SQLException {
        String externalId = conversation.externalConversationId();
        var existing = chats.findByExternalIdentity(Source.chatgptJson, externalId);
        if (existing.isEmpty()) {
            return Outcome.inserted;
        }
        Chat stored = existing.get();
        String incomingHash = ChatContentHasher.hash(conversation.importedChat().messages());
        return incomingHash.equals(stored.contentHash()) ? Outcome.unchanged : Outcome.updated;
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
