package chatmap.application.service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import chatmap.domain.Project;

/** Active software project receiving a routed prompt. */
public record ProjectContext(
        long projectId,
        String chatMapProjectIdentity,
        String workingProjectIdentity,
        Optional<Path> repositoryPath) {

    public ProjectContext {
        if (projectId < 0) {
            throw new IllegalArgumentException("projectId must not be negative");
        }
        chatMapProjectIdentity = requireNonblank(chatMapProjectIdentity, "chatMapProjectIdentity");
        workingProjectIdentity = requireNonblank(workingProjectIdentity, "workingProjectIdentity");
        Objects.requireNonNull(repositoryPath, "repositoryPath");
    }

    public static ProjectContext of(String workingProjectIdentity, Path repositoryPath) {
        return new ProjectContext(0, "chatmap", workingProjectIdentity, Optional.ofNullable(repositoryPath));
    }

    public static ProjectContext from(Project project) {
        Objects.requireNonNull(project, "project");
        if (project.id() <= 0) {
            throw new IllegalArgumentException("project must have a stable persisted id");
        }
        Path path = project.repositoryPath() == null || project.repositoryPath().isBlank()
                ? null
                : Path.of(project.repositoryPath());
        return new ProjectContext(project.id(), "chatmap", project.name(), Optional.ofNullable(path));
    }

    private static String requireNonblank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
