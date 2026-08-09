package chatmap.domain;

import java.util.List;

/** Inventory results for one configured provider. */
public record ProviderInventory(
        String providerName,
        List<InventoryEntry> conversations,
        boolean complete,
        String diagnostic) {

    public ProviderInventory {
        conversations = List.copyOf(conversations);
    }
}
