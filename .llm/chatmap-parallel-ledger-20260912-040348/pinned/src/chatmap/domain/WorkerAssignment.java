package chatmap.domain;

import java.util.Optional;

/** Structured work assignment preserving the five handoff-required input fields. */
public record WorkerAssignment(
        long id,
        Long predecessorSessionId,
        String task,
        String contextAndFiles,
        String availableTools,
        String constraintsAndPermissions,
        String definitionOfDone,
        String escalationBehavior,
        String createdAt) {

    public Optional<Long> predecessorSession() {
        return Optional.ofNullable(predecessorSessionId);
    }
}
