package chatmap.domain;

import java.util.Objects;

/**
 * A project groups related chats.
 *
 * Timestamps are UTC ISO-8601 strings (see design.md, Core Data Model).
 */
public record Project(
        long id,
        String name,
        String description,
        String repositoryPath,
        String createdAt,
        String updatedAt) {

    public Project {
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative");
        }
        name = requireNonblank(name, "name");
        repositoryPath = blankToNull(repositoryPath);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Project(long id, String name, String description, String createdAt, String updatedAt) {
        this(id, name, description, null, createdAt, updatedAt);
    }

    private static String requireNonblank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
