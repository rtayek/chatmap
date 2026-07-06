package chatmap.domain;

/** Association between a chat and a tag. */
public record ChatTag(
        long chatId,
        long tagId) {
}
