package chatmap.domain;

import java.util.EnumSet;
import java.util.Set;

/** Explicit lifecycle states for the worker-continuity experiment. */
public enum WorkerLifecycleState {
    QUEUED,
    WORKING,
    WAITING_FOR_DECISION,
    COMPLETED,
    FAILED,
    CANCELLED,
    RETIRED;

    public boolean canTransitionTo(WorkerLifecycleState next) {
        return allowedNextStates().contains(next);
    }

    public Set<WorkerLifecycleState> allowedNextStates() {
        return switch (this) {
            case QUEUED -> EnumSet.of(WORKING, CANCELLED);
            case WORKING -> EnumSet.of(WAITING_FOR_DECISION, COMPLETED, FAILED, CANCELLED);
            case WAITING_FOR_DECISION -> EnumSet.of(WORKING, FAILED, CANCELLED);
            case COMPLETED, FAILED, CANCELLED -> EnumSet.of(RETIRED);
            case RETIRED -> EnumSet.noneOf(WorkerLifecycleState.class);
        };
    }
}
