package chatmap.application.port.importing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chatmap.application.model.ImportedChat;

/** Reads ChatGPT archive data without exposing ZIP or JSON implementation details. */
public interface ChatArchiveReader {

    ArchiveReadResult read(Path archive, String importedAt) throws IOException;

    CodexInspection inspectCodex(Path archive) throws IOException;

    record ImportedConversation(String entryName, String externalConversationId, ImportedChat importedChat) {
    }

    record Failure(String entryName, String conversationId, String reason) {
    }

    record ArchiveReadResult(
            Path archivePath,
            List<String> conversationEntries,
            int conversationsDiscovered,
            List<ImportedConversation> conversations,
            int skipped,
            int unsupportedContentParts,
            Map<String, Integer> unsupportedContentCategories,
            List<Failure> failures) {
        public ArchiveReadResult {
            conversationEntries = List.copyOf(conversationEntries);
            conversations = List.copyOf(conversations);
            unsupportedContentCategories = Map.copyOf(unsupportedContentCategories);
            failures = List.copyOf(failures);
        }
    }

    record CodexInspection(
            boolean present,
            String topLevelType,
            int recordCount,
            Set<String> sampleKeys,
            boolean normalConversationSchema,
            boolean codexTurnSchema) {
        public CodexInspection {
            sampleKeys = Set.copyOf(sampleKeys);
        }
    }
}
