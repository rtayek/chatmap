package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProviderInventoryTest {

    private static InventoryEntry sampleEntry() {
        return new InventoryEntry(new ConversationCandidate(Source.plainText, "ext-1", "Title", "uri", null), null);
    }

    @Test
    void conversationsListIsDefensivelyCopiedSoCallerMutationDoesNotLeakIn() {
        List<InventoryEntry> mutable = new ArrayList<>(List.of(sampleEntry()));
        ProviderInventory inventory = new ProviderInventory("Claude Code", mutable, true, null);

        mutable.add(sampleEntry());

        assertEquals(1, inventory.conversations().size());
    }

    @Test
    void conversationsListReturnedIsUnmodifiable() {
        ProviderInventory inventory = new ProviderInventory("Claude Code", List.of(sampleEntry()), true, null);

        assertThrows(UnsupportedOperationException.class, () -> inventory.conversations().add(sampleEntry()));
    }
}
