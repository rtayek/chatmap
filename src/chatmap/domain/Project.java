package chatmap.domain;

/**
 * A project groups related chats.
 *
 * Timestamps are UTC ISO-8601 strings (see design.md, Core Data Model).
 */
public record Project(
        long id,
        String name,
        String description,
        String createdAt,
        String updatedAt) {
}
