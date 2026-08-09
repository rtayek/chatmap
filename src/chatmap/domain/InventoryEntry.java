package chatmap.domain;

/** One discovered conversation plus its local imported-chat status. */
public record InventoryEntry(ConversationCandidate candidate, Long importedChatId) {

    public boolean alreadyImported() {
        return importedChatId != null;
    }
}
