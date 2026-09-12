package chatmap.domain;

import java.util.Optional;

/** Artifact produced by a worker session. */
public record WorkerArtifact(
        long id,
        long sessionId,
        String label,
        String location,
        String description,
        String createdAt) {

    public Optional<String> detail() {
        return Optional.ofNullable(description);
    }
}
