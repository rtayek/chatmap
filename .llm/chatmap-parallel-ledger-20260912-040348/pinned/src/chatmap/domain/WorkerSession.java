package chatmap.domain;

/** One worker's attempt to execute a structured assignment. */
public record WorkerSession(
        long id,
        long assignmentId,
        String workerIdentity,
        WorkerLifecycleState lifecycleState,
        String createdAt,
        String updatedAt) {
}
