package chatmap.application.service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Active software project receiving a routed prompt. */
public record ProjectContext(
        String chatMapProjectIdentity,
        String workingProjectIdentity,
        Optional<Path> repositoryPath) {

    public ProjectContext {
        chatMapProjectIdentity = requireNonblank(chatMapProjectIdentity, "chatMapProjectIdentity");
        workingProjectIdentity = requireNonblank(workingProjectIdentity, "workingProjectIdentity");
        Objects.requireNonNull(repositoryPath, "repositoryPath");
    }

    public static ProjectContext of(String workingProjectIdentity, Path repositoryPath) {
        return new ProjectContext("chatmap", workingProjectIdentity, Optional.ofNullable(repositoryPath));
    }

    private static String requireNonblank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
