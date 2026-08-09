package chatmap.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import chatmap.domain.ConversationCandidate;
import chatmap.domain.ConversationInventory;
import chatmap.domain.InventoryEntry;
import chatmap.domain.ProviderInventory;
import chatmap.domain.Source;

class ConversationInventoryFormatterTest {

    @Test
    void formatsMultipleProvidersEntriesAndDiagnostics() {
        ConversationInventory inventory = new ConversationInventory(List.of(
                new ProviderInventory("Claude (web)", List.of(
                        new InventoryEntry(new ConversationCandidate(Source.claudeWeb,
                                "claude-1", "Claude Title", "https://claude.ai/chat/claude-1", null), 42L)),
                        false, "Sidebar may be incomplete."),
                new ProviderInventory("Codex (CLI)", List.of(
                        new InventoryEntry(new ConversationCandidate(Source.codexCli,
                                "2026/08/09/rollout.jsonl", "Rollout",
                                "file:///home/ray/.codex/sessions/rollout.jsonl",
                                "2026-08-09T00:00:00Z"), null)),
                        true, "")));

        String formatted = ConversationInventoryFormatter.format(inventory);

        assertTrue(formatted.contains("Claude (web)  count=1  imported=1  missing=0  complete=false"));
        assertTrue(formatted.contains("diagnostic: Sidebar may be incomplete."));
        assertTrue(formatted.contains("imported  claudeWeb  claude-1"));
        assertTrue(formatted.contains("Codex (CLI)  count=1  imported=0  missing=1  complete=true"));
        assertTrue(formatted.contains("missing  codexCli  2026/08/09/rollout.jsonl"));
    }
}
