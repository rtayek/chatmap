package chatmap.domain;

/** Metadata for one provider conversation discovered without importing its transcript. */
public record ConversationCandidate(
        Source source,
        String externalConversationId,
        String title,
        String sourceUri,
        String updatedAt) {
}
