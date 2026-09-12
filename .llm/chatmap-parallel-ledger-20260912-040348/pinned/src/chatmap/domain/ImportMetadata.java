package chatmap.domain;

/** Source identity and refresh state for an imported chat. */
public record ImportMetadata(
        String externalConversationId,
        String sourceUri,
        String transcriptHash,
        String sourceUpdatedAt,
        String lastImportedAt) {

    public static ImportMetadata none(String sourceUpdatedAt, String lastImportedAt) {
        return new ImportMetadata(null, null, null, sourceUpdatedAt, lastImportedAt);
    }
}
