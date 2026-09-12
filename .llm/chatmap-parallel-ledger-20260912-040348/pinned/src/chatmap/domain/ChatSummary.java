package chatmap.domain;

/**
 * An LLM-generated summary of a chat.
 *
 * This is a derived artifact, not source data: it never replaces or edits
 * the chat's messages, and a chat may have more than one summary over time
 * (id is unique per row, not per chat). generatedBy records which backend
 * produced it (e.g. "claude"), so it stays honest about provenance and can
 * later support summarizing with a different backend than the one that
 * produced the chat.
 */
public record ChatSummary(
        long id,
        long chatId,
        String summary,
        String generatedBy,
        String generatedAt,
        String contentHash) {

    public ChatSummary(long id, long chatId, String summary, String generatedBy,
            String generatedAt) {
        this(id, chatId, summary, generatedBy, generatedAt, null);
    }
}
