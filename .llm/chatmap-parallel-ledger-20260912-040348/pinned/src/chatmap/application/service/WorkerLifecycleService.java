package chatmap.application.service;

import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chatmap.application.port.persistence.TransactionManager;
import chatmap.application.port.persistence.WorkerLifecycleStore;
import chatmap.domain.WorkerArtifact;
import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleChain;
import chatmap.domain.WorkerLifecycleEvent;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSemanticHandoff;
import chatmap.domain.WorkerSession;

/** Deterministic service for the worker-lifecycle continuity experiment. */
public final class WorkerLifecycleService {
    private final WorkerLifecycleStore store;
    private final TransactionManager transactions;
    private final Clock clock;

    public WorkerLifecycleService(WorkerLifecycleStore store, TransactionManager transactions, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WorkerAssignment createAssignment(WorkerAssignmentInput input) throws SQLException {
        requireInput(input);
        String now = now();
        return store.insertAssignment(new WorkerAssignment(0, null, input.task(), input.contextAndFiles(),
                input.availableTools(), input.constraintsAndPermissions(), input.definitionOfDone(),
                input.escalationBehavior(), now));
    }

    public WorkerAssignment createSuccessorAssignment(long predecessorSessionId, WorkerAssignmentInput input)
            throws SQLException {
        requireInput(input);
        requireSession(predecessorSessionId);
        String now = now();
        return store.insertAssignment(new WorkerAssignment(0, predecessorSessionId, input.task(),
                input.contextAndFiles(), input.availableTools(), input.constraintsAndPermissions(),
                input.definitionOfDone(), input.escalationBehavior(), now));
    }

    public WorkerSession createSession(long assignmentId, String workerIdentity) throws SQLException {
        requireAssignment(assignmentId);
        String worker = requireText(workerIdentity, "Worker identity");
        String now = now();
        return store.insertSession(new WorkerSession(0, assignmentId, worker, WorkerLifecycleState.QUEUED, now, now));
    }

    public WorkerSession transition(long sessionId, WorkerLifecycleState nextState) throws SQLException {
        return transition(sessionId, nextState, null);
    }

    public WorkerSession transition(long sessionId, WorkerLifecycleState nextState, DecisionRequest decision)
            throws SQLException {
        Objects.requireNonNull(nextState, "nextState");
        return transactions.inTransaction(() -> {
            WorkerSession session = requireSession(sessionId);
            validateTransition(session.lifecycleState(), nextState);
            DecisionRequest actualDecision = nextState == WorkerLifecycleState.WAITING_FOR_DECISION
                    ? requireDecision(decision) : null;
            String now = now();
            store.insertEvent(new WorkerLifecycleEvent(0, sessionId, session.lifecycleState(), nextState,
                    actualDecision == null ? null : actualDecision.question(),
                    actualDecision == null ? null : actualDecision.reason(),
                    actualDecision == null ? null : actualDecision.partialWork(), now));
            return store.updateSessionState(sessionId, nextState, now);
        });
    }

    public WorkerArtifact addArtifact(long sessionId, String label, String location, String description)
            throws SQLException {
        requireSession(sessionId);
        return store.insertArtifact(new WorkerArtifact(0, sessionId, requireText(label, "Artifact label"),
                requireText(location, "Artifact location"), blankToNull(description), now()));
    }

    public WorkerSemanticHandoff storeHandoff(long sessionId, WorkerSemanticHandoffInput input) throws SQLException {
        requireHandoffInput(input);
        WorkerSession session = requireSession(sessionId);
        if (session.lifecycleState() != WorkerLifecycleState.COMPLETED) {
            throw new IllegalStateException("Semantic handoff requires COMPLETED session, found "
                    + session.lifecycleState());
        }
        return store.insertHandoff(new WorkerSemanticHandoff(0, sessionId, input.workCompleted(),
                input.decisionsAndReasons(), input.artifactsAndLocations(), input.unresolvedProblems(),
                input.requiredUserDecisions(), input.recommendedNextAction(), blankToNull(input.successorTask()),
                blankToNull(input.successorContextAndFiles()), blankToNull(input.successorAvailableTools()),
                blankToNull(input.successorConstraintsAndPermissions()),
                blankToNull(input.successorDefinitionOfDone()), blankToNull(input.successorEscalationBehavior()),
                now()));
    }

    public WorkerLifecycleRecord record(long sessionId) throws SQLException {
        WorkerSession session = requireSession(sessionId);
        WorkerAssignment assignment = requireAssignment(session.assignmentId());
        return new WorkerLifecycleRecord(assignment, session, store.findEvents(sessionId),
                store.findArtifacts(sessionId), store.findHandoff(sessionId),
                store.findSuccessorAssignments(sessionId));
    }

    public WorkerLifecycleChain chainFrom(long sessionId) throws SQLException {
        List<WorkerLifecycleRecord> records = new ArrayList<>();
        collect(sessionId, records);
        return new WorkerLifecycleChain(records);
    }

    private void collect(long sessionId, List<WorkerLifecycleRecord> records) throws SQLException {
        WorkerLifecycleRecord current = record(sessionId);
        records.add(current);
        for (WorkerAssignment successor : current.successorAssignments()) {
            WorkerSession successorSession = store.findSessionByAssignment(successor.id()).orElse(null);
            if (successorSession != null) {
                collect(successorSession.id(), records);
            }
        }
    }

    private WorkerAssignment requireAssignment(long assignmentId) throws SQLException {
        return store.findAssignment(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown worker assignment: " + assignmentId));
    }

    private WorkerSession requireSession(long sessionId) throws SQLException {
        return store.findSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown worker session: " + sessionId));
    }

    private static void validateTransition(WorkerLifecycleState current, WorkerLifecycleState next) {
        if (!current.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid worker lifecycle transition: " + current + " -> " + next);
        }
    }

    private static DecisionRequest requireDecision(DecisionRequest decision) {
        if (decision == null) {
            throw new IllegalArgumentException("Decision request is required for WAITING_FOR_DECISION.");
        }
        return new DecisionRequest(
                requireText(decision.question(), "Decision question"),
                requireText(decision.reason(), "Decision reason"),
                requireText(decision.partialWork(), "Preserved partial work"));
    }

    private static void requireInput(WorkerAssignmentInput input) {
        Objects.requireNonNull(input, "input");
        requireText(input.task(), "Task");
        requireText(input.contextAndFiles(), "Context and files");
        requireText(input.availableTools(), "Available tools");
        requireText(input.constraintsAndPermissions(), "Constraints and permissions");
        requireText(input.definitionOfDone(), "Definition of done");
        requireText(input.escalationBehavior(), "Escalation behavior");
    }

    private static void requireHandoffInput(WorkerSemanticHandoffInput input) {
        Objects.requireNonNull(input, "input");
        requireText(input.workCompleted(), "Work completed");
        requireText(input.decisionsAndReasons(), "Decisions and reasons");
        requireText(input.artifactsAndLocations(), "Artifacts and locations");
        requireText(input.unresolvedProblems(), "Unresolved problems");
        requireText(input.requiredUserDecisions(), "Required user decisions");
        requireText(input.recommendedNextAction(), "Recommended next action");
    }

    private static String requireText(String text, String label) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return trimmed;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String now() {
        return clock.instant().toString();
    }

    public record WorkerAssignmentInput(
            String task,
            String contextAndFiles,
            String availableTools,
            String constraintsAndPermissions,
            String definitionOfDone,
            String escalationBehavior) {
    }

    public record DecisionRequest(String question, String reason, String partialWork) {
    }

    public record WorkerSemanticHandoffInput(
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
            String successorEscalationBehavior) {
    }
}
