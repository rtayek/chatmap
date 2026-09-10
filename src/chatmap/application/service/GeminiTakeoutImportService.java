package chatmap.application.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import chatmap.application.port.importing.ConversationFileReader;

/** Imports validated Gemini conversations through the existing transactional import service. */
public final class GeminiTakeoutImportService {
    public GeminiTakeoutImportService(ConversationFileReader reader, ImportService importService) {
        this.reader = reader;
        this.importService = importService;
    }

    public BulkImportResult importDirectory(Path directory) throws IOException {
        var conversations = reader.read(directory, Instant.now().toString());
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<>();
        for (var conversation : conversations) {
            if (conversation.messages().stream().allMatch(message -> message.text().isBlank())) {
                skipped++;
                continue;
            }
            try {
                switch (importService.persist(conversation).outcome()) {
                    case inserted -> inserted++;
                    case updated -> updated++;
                    case unchanged -> unchanged++;
                }
            } catch (SQLException | RuntimeException e) {
                failures.add(conversation.chat().externalConversationId() + ": " + e.getMessage());
            }
        }
        return new BulkImportResult(conversations.size(), inserted, updated, unchanged, skipped, failures);
    }

    public record BulkImportResult(int discovered, int inserted, int updated, int unchanged,
            int skipped, List<String> failures) {
        public BulkImportResult {
            failures = List.copyOf(failures);
        }

        public String summary() {
            return "Gemini Takeout: discovered " + discovered + ", inserted " + inserted
                    + ", updated " + updated + ", unchanged " + unchanged + ", skipped " + skipped
                    + ", failed " + failures.size();
        }
    }

    private final ConversationFileReader reader;
    private final ImportService importService;
}
