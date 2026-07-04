package chatmap.domain;

/**
 * A tag. Names are unique case-insensitively (enforced by the schema:
 * UNIQUE COLLATE NOCASE), so "MVP" and "mvp" are the same tag.
 */
public record Tag(
        long id,
        String name) {
}
