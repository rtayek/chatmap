package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InventoryEntryTest {

    private static ConversationCandidate sampleCandidate() {
        return new ConversationCandidate(Source.plainText, "ext-1", "Title", "uri", null);
    }

    @Test
    void nullImportedChatIdMeansNotYetImported() {
        InventoryEntry entry = new InventoryEntry(sampleCandidate(), null);

        assertFalse(entry.alreadyImported());
    }

    @Test
    void nonNullImportedChatIdMeansAlreadyImported() {
        InventoryEntry entry = new InventoryEntry(sampleCandidate(), 42L);

        assertTrue(entry.alreadyImported());
    }
}
