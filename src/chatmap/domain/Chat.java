package chatmap.domain;

/**
 * One imported conversation.
 *
 * A chat belongs to zero or one project in the MVP; projectId is null when
 * unassigned. Timestamps are UTC ISO-8601 strings; createdAt/updatedAt may be
 * null when the source format does not provide them (importedAt never is).
 */
public record Chat(
        long id,
        Long projectId,
        Source source,
        String title,
        String createdAt,
        String updatedAt,
        String importedAt,
        boolean archived,
        String externalConversationId,
        String sourceUri,
        String contentHash,
        String sourceUpdatedAt,
        String lastImportedAt) {

    public Chat(long id, Long projectId, Source source, String title, String createdAt,
            String updatedAt, String importedAt, boolean archived) {
        this(id, projectId, source, title, createdAt, updatedAt, importedAt, archived,
                null, null, null, updatedAt, importedAt);
    }
}
