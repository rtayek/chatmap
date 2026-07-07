package chatmap.domain;

import java.util.List;

/** Display-ready chat search result with optional organization metadata. */
public record SearchResult(
        Chat chat,
        String projectName,
        List<Tag> tags,
        String snippet) {

    public SearchResult {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public long chatId() {
        return chat.id();
    }
}
