package chatmap.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chatmap.backend.providers.ChatProvider;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.ConversationInventory;
import chatmap.domain.InventoryEntry;
import chatmap.domain.ProviderInventory;
import chatmap.storage.ChatRepository;

/** Read-only inventory of all conversations discoverable from configured providers. */
public final class ConversationInventoryService {

    private final List<ChatProvider> providers;
    private final ChatRepository chats;

    public ConversationInventoryService(List<ChatProvider> providers, ChatRepository chats) {
        this.providers = List.copyOf(providers);
        this.chats = chats;
    }

    public ConversationInventory inventory() throws SQLException {
        List<ProviderDiscovery> discoveries = new ArrayList<>();
        List<ConversationCandidate> allCandidates = new ArrayList<>();
        for (ChatProvider provider : providers) {
            ProviderDiscovery discovery = discover(provider);
            discoveries.add(discovery);
            allCandidates.addAll(discovery.candidates());
        }

        Map<String, Long> importedIds = chats.findImportedIdsByExternalIdentity(allCandidates);
        List<ProviderInventory> inventories = new ArrayList<>();
        for (ProviderDiscovery discovery : discoveries) {
            List<InventoryEntry> entries = new ArrayList<>();
            for (ConversationCandidate candidate : discovery.candidates()) {
                Long importedChatId = null;
                if (candidate.externalConversationId() != null
                        && !candidate.externalConversationId().isBlank()) {
                    importedChatId = importedIds.get(ChatRepository.identityKey(
                            candidate.source(), candidate.externalConversationId()));
                }
                entries.add(new InventoryEntry(candidate, importedChatId));
            }
            inventories.add(new ProviderInventory(discovery.providerName(), entries,
                    discovery.complete(), discovery.diagnostic()));
        }
        return new ConversationInventory(inventories);
    }

    private static ProviderDiscovery discover(ChatProvider provider) {
        try {
            DedupedCandidates deduped = dedupe(provider.listChats());
            String diagnostic = provider.inventoryDiagnostic().orElse("");
            if (deduped.removed() > 0) {
                diagnostic = appendDiagnostic(diagnostic,
                        "Removed " + deduped.removed() + " duplicate candidate(s).");
            }
            return new ProviderDiscovery(provider.name(), deduped.candidates(),
                    provider.inventoryComplete(), diagnostic);
        } catch (Exception failure) {
            return new ProviderDiscovery(provider.name(), List.of(), false, diagnostic(failure));
        }
    }

    private static DedupedCandidates dedupe(Collection<ConversationCandidate> candidates) {
        Map<String, ConversationCandidate> byKey = new LinkedHashMap<>();
        int duplicates = 0;
        for (ConversationCandidate candidate : candidates) {
            String key = candidateKey(candidate);
            if (byKey.containsKey(key)) {
                duplicates++;
            } else {
                byKey.put(key, candidate);
            }
        }
        return new DedupedCandidates(List.copyOf(byKey.values()), duplicates);
    }

    private static String candidateKey(ConversationCandidate candidate) {
        if (candidate.externalConversationId() != null
                && !candidate.externalConversationId().isBlank()) {
            return candidate.source().dbValue() + "\u001f" + candidate.externalConversationId();
        }
        return candidate.source().dbValue() + "\u001f" + candidate.sourceUri();
    }

    private static String appendDiagnostic(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + " " + addition;
    }

    private static String diagnostic(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return failure.getClass().getSimpleName() + ": " + message;
    }

    private record ProviderDiscovery(
            String providerName,
            List<ConversationCandidate> candidates,
            boolean complete,
            String diagnostic) {
    }

    private record DedupedCandidates(List<ConversationCandidate> candidates, int removed) {
    }
}
