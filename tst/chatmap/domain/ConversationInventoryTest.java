package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConversationInventoryTest {

    private static ProviderInventory sampleProvider() {
        return new ProviderInventory("Claude Code", List.of(), true, null);
    }

    @Test
    void providersListIsDefensivelyCopiedSoCallerMutationDoesNotLeakIn() {
        List<ProviderInventory> mutable = new ArrayList<>(List.of(sampleProvider()));
        ConversationInventory inventory = new ConversationInventory(mutable);

        mutable.add(sampleProvider());

        assertEquals(1, inventory.providers().size());
    }

    @Test
    void providersListReturnedIsUnmodifiable() {
        ConversationInventory inventory = new ConversationInventory(List.of(sampleProvider()));

        assertThrows(UnsupportedOperationException.class, () -> inventory.providers().add(sampleProvider()));
    }
}
