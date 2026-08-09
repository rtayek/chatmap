package chatmap.ui;

import chatmap.domain.ConversationCandidate;
import chatmap.domain.ConversationInventory;
import chatmap.domain.InventoryEntry;
import chatmap.domain.ProviderInventory;

/** Formats provider-grouped conversation inventory for the JavaFX dialog. */
public final class ConversationInventoryFormatter {

    private ConversationInventoryFormatter() {
    }

    public static String format(ConversationInventory inventory) {
        StringBuilder text = new StringBuilder();
        for (ProviderInventory provider : inventory.providers()) {
            appendProvider(text, provider);
        }
        return text.toString().stripTrailing();
    }

    private static void appendProvider(StringBuilder text, ProviderInventory provider) {
        long imported = provider.conversations().stream().filter(InventoryEntry::alreadyImported).count();
        long missing = provider.conversations().size() - imported;
        text.append(provider.providerName())
                .append("  count=").append(provider.conversations().size())
                .append("  imported=").append(imported)
                .append("  missing=").append(missing)
                .append("  complete=").append(provider.complete())
                .append(System.lineSeparator());
        if (provider.diagnostic() != null && !provider.diagnostic().isBlank()) {
            text.append("diagnostic: ").append(provider.diagnostic()).append(System.lineSeparator());
        }
        for (InventoryEntry entry : provider.conversations()) {
            appendEntry(text, entry);
        }
        text.append(System.lineSeparator());
    }

    private static void appendEntry(StringBuilder text, InventoryEntry entry) {
        ConversationCandidate candidate = entry.candidate();
        text.append(entry.alreadyImported() ? "imported" : "missing")
                .append("  ")
                .append(candidate.source().dbValue())
                .append("  ")
                .append(nullToBlank(candidate.externalConversationId()))
                .append("  ")
                .append(nullToBlank(candidate.updatedAt()))
                .append("  ")
                .append(nullToBlank(candidate.title()))
                .append(System.lineSeparator());
        if (candidate.sourceUri() != null && !candidate.sourceUri().isBlank()) {
            text.append("  ").append(candidate.sourceUri()).append(System.lineSeparator());
        }
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
