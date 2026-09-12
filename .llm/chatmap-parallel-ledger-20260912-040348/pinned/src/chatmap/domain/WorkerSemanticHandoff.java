package chatmap.domain;

/** Semantic handoff produced by a completed worker session. */
public record WorkerSemanticHandoff(
        long id,
        long sessionId,
        String workCompleted,
        String decisionsAndReasons,
        String artifactsAndLocations,
        String unresolvedProblems,
        String requiredUserDecisions,
        String recommendedNextAction,
        String successorTask,
        String successorContextAndFiles,
        String successorAvailableTools,
        String successorConstraintsAndPermissions,
        String successorDefinitionOfDone,
        String successorEscalationBehavior,
        String createdAt) {
}
