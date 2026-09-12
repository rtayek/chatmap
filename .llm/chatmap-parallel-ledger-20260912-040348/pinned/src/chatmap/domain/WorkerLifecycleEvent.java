package chatmap.domain;

import java.util.Optional;

/** Immutable audit row for one lifecycle transition. */
public record WorkerLifecycleEvent(
        long id,
        long sessionId,
        WorkerLifecycleState fromState,
        WorkerLifecycleState toState,
        String question,
        String reason,
        String partialWork,
        String createdAt) {

    public Optional<String> decisionQuestion() {
        return Optional.ofNullable(question);
    }

    public Optional<String> decisionReason() {
        return Optional.ofNullable(reason);
    }

    public Optional<String> preservedPartialWork() {
        return Optional.ofNullable(partialWork);
    }
}
