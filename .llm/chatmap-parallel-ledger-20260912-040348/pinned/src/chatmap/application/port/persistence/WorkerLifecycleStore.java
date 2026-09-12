package chatmap.application.port.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import chatmap.domain.WorkerArtifact;
import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleEvent;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSemanticHandoff;
import chatmap.domain.WorkerSession;

/** Persistence operations for the worker-lifecycle continuity experiment. */
public interface WorkerLifecycleStore {
    WorkerAssignment insertAssignment(WorkerAssignment assignment) throws SQLException;

    WorkerSession insertSession(WorkerSession session) throws SQLException;

    WorkerLifecycleEvent insertEvent(WorkerLifecycleEvent event) throws SQLException;

    WorkerSession updateSessionState(long sessionId, WorkerLifecycleState state, String updatedAt)
            throws SQLException;

    WorkerArtifact insertArtifact(WorkerArtifact artifact) throws SQLException;

    WorkerSemanticHandoff insertHandoff(WorkerSemanticHandoff handoff) throws SQLException;

    Optional<WorkerAssignment> findAssignment(long assignmentId) throws SQLException;

    Optional<WorkerSession> findSession(long sessionId) throws SQLException;

    Optional<WorkerSession> findSessionByAssignment(long assignmentId) throws SQLException;

    List<WorkerLifecycleEvent> findEvents(long sessionId) throws SQLException;

    List<WorkerArtifact> findArtifacts(long sessionId) throws SQLException;

    Optional<WorkerSemanticHandoff> findHandoff(long sessionId) throws SQLException;

    List<WorkerAssignment> findSuccessorAssignments(long predecessorSessionId) throws SQLException;
}
