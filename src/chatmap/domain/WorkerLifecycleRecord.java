package chatmap.domain;

import java.util.List;
import java.util.Optional;

/** Hydrated work record for answering who did what and what should happen next. */
public record WorkerLifecycleRecord(
        WorkerAssignment assignment,
        WorkerSession session,
        List<WorkerLifecycleEvent> events,
        List<WorkerArtifact> artifacts,
        Optional<WorkerSemanticHandoff> handoff,
        List<WorkerAssignment> successorAssignments) {

    public WorkerLifecycleRecord {
        events = List.copyOf(events);
        artifacts = List.copyOf(artifacts);
        successorAssignments = List.copyOf(successorAssignments);
    }
}
