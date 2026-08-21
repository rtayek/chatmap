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
        String localPath,
        String remoteUrl,
        String createdAt,
        String updatedAt) {

    public Project {
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative");
        }
        name = requireNonblank(name, "name");
        repositoryPath = blankToNull(repositoryPath);
        localPath = blankToNull(localPath);
        remoteUrl = blankToNull(remoteUrl);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Project(long id, String name, String description, String localPath, String remoteUrl,
            String createdAt, String updatedAt) {
        this(id, name, description, localPath, localPath, remoteUrl, createdAt, updatedAt);
    }

    public Project(long id, String name, String description, String repositoryPath,
            String createdAt, String updatedAt) {
        this(id, name, description, repositoryPath, repositoryPath, null, createdAt, updatedAt);
    }

    public Project(long id, String name, String description, String createdAt, String updatedAt) {
        this(id, name, description, null, null, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return localPath == null ? name : name + " (" + localPath + ")";
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
