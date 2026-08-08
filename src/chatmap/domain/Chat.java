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

    /**
     * A mutable copy of this chat. Callers change fields by name and {@link Builder#build()},
     * instead of rebuilding all 13 fields positionally (which risks silently transposing
     * the many adjacent String fields).
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Copy builder for {@link Chat}. Start from {@link Chat#toBuilder()}. */
    public static final class Builder {
        private final long id;
        private final Source source;
        private Long projectId;
        private String title;
        private String createdAt;
        private String updatedAt;
        private String importedAt;
        private boolean archived;
        private String externalConversationId;
        private String sourceUri;
        private String contentHash;
        private String sourceUpdatedAt;
        private String lastImportedAt;

        private Builder(Chat chat) {
            this.id = chat.id;
            this.source = chat.source;
            this.projectId = chat.projectId;
            this.title = chat.title;
            this.createdAt = chat.createdAt;
            this.updatedAt = chat.updatedAt;
            this.importedAt = chat.importedAt;
            this.archived = chat.archived;
            this.externalConversationId = chat.externalConversationId;
            this.sourceUri = chat.sourceUri;
            this.contentHash = chat.contentHash;
            this.sourceUpdatedAt = chat.sourceUpdatedAt;
            this.lastImportedAt = chat.lastImportedAt;
        }

        public Builder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder archived(boolean archived) {
            this.archived = archived;
            return this;
        }

        public Builder externalConversationId(String externalConversationId) {
            this.externalConversationId = externalConversationId;
            return this;
        }

        public Builder sourceUri(String sourceUri) {
            this.sourceUri = sourceUri;
            return this;
        }

        public Builder contentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public Builder sourceUpdatedAt(String sourceUpdatedAt) {
            this.sourceUpdatedAt = sourceUpdatedAt;
            return this;
        }

        public Builder lastImportedAt(String lastImportedAt) {
            this.lastImportedAt = lastImportedAt;
            return this;
        }

        public Chat build() {
            return new Chat(id, projectId, source, title, createdAt, updatedAt, importedAt,
                    archived, externalConversationId, sourceUri, contentHash, sourceUpdatedAt,
                    lastImportedAt);
        }
    }
}
