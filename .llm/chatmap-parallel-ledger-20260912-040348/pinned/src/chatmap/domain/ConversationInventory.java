package chatmap.domain;

import java.util.List;

/** Provider-grouped inventory across all configured chat sources. */
public record ConversationInventory(List<ProviderInventory> providers) {

    public ConversationInventory {
        providers = List.copyOf(providers);
    }
}
