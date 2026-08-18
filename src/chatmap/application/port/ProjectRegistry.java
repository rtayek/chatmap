package chatmap.application.port;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class ProjectRegistry {
    private final Map<String, Path> registry;

    public ProjectRegistry(Map<String, Path> registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Project registry cannot be null");
        }
        this.registry = Map.copyOf(registry);
    }

    public Optional<Path> pathFor(String projectKey) {
        if (projectKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry.get(projectKey));
    }
}
