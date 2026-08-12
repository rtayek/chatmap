package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ImportMetadataTest {

    @Test
    void nonePlacesArgumentsIntoSourceUpdatedAtAndLastImportedAtWithoutTransposing() {
        ImportMetadata metadata = ImportMetadata.none("2026-01-02T00:00:00Z", "2026-01-03T00:00:00Z");

        assertEquals("2026-01-02T00:00:00Z", metadata.sourceUpdatedAt());
        assertEquals("2026-01-03T00:00:00Z", metadata.lastImportedAt());
        assertNull(metadata.externalConversationId());
        assertNull(metadata.sourceUri());
        assertNull(metadata.transcriptHash());
    }
}
