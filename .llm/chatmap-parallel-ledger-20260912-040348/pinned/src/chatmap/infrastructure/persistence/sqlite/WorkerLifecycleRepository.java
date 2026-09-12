package chatmap.infrastructure.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.application.port.persistence.WorkerLifecycleStore;
import chatmap.domain.WorkerArtifact;
import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleEvent;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSemanticHandoff;
import chatmap.domain.WorkerSession;

/** SQLite persistence for worker lifecycle continuity records. */
public final class WorkerLifecycleRepository implements WorkerLifecycleStore {
    private final Connection conn;

    public WorkerLifecycleRepository(Connection conn) {
        this.conn = conn;
    }

    @Override
    public WorkerAssignment insertAssignment(WorkerAssignment assignment) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT INTO workerAssignments (predecessorSessionId, task, contextAndFiles, "
                    + "availableTools, constraintsAndPermissions, definitionOfDone, escalationBehavior, createdAt) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                setLongOrNull(ps, 1, assignment.predecessorSessionId());
                ps.setString(2, assignment.task());
                ps.setString(3, assignment.contextAndFiles());
                ps.setString(4, assignment.availableTools());
                ps.setString(5, assignment.constraintsAndPermissions());
                ps.setString(6, assignment.definitionOfDone());
                ps.setString(7, assignment.escalationBehavior());
                ps.setString(8, assignment.createdAt());
                ps.executeUpdate();
                return withId(assignment, generatedId(ps));
            }
        }
    }

    @Override
    public WorkerSession insertSession(WorkerSession session) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT INTO workerSessions (assignmentId, workerIdentity, lifecycleState, "
                    + "createdAt, updatedAt) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, session.assignmentId());
                ps.setString(2, session.workerIdentity());
                ps.setString(3, session.lifecycleState().name());
                ps.setString(4, session.createdAt());
                ps.setString(5, session.updatedAt());
                ps.executeUpdate();
                return withId(session, generatedId(ps));
            }
        }
    }

    @Override
    public WorkerLifecycleEvent insertEvent(WorkerLifecycleEvent event) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT INTO workerLifecycleEvents (sessionId, fromState, toState, question, "
                    + "reason, partialWork, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, event.sessionId());
                ps.setString(2, event.fromState().name());
                ps.setString(3, event.toState().name());
                ps.setString(4, event.question());
                ps.setString(5, event.reason());
                ps.setString(6, event.partialWork());
                ps.setString(7, event.createdAt());
                ps.executeUpdate();
                return withId(event, generatedId(ps));
            }
        }
    }

    @Override
    public WorkerSession updateSessionState(long sessionId, WorkerLifecycleState state, String updatedAt)
            throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE workerSessions SET lifecycleState = ?, updatedAt = ? WHERE id = ?")) {
                ps.setString(1, state.name());
                ps.setString(2, updatedAt);
                ps.setLong(3, sessionId);
                ps.executeUpdate();
            }
            return findSession(sessionId).orElseThrow(() -> new SQLException("Unknown worker session: " + sessionId));
        }
    }

    @Override
    public WorkerArtifact insertArtifact(WorkerArtifact artifact) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT INTO workerArtifacts (sessionId, label, location, description, createdAt) "
                    + "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, artifact.sessionId());
                ps.setString(2, artifact.label());
                ps.setString(3, artifact.location());
                ps.setString(4, artifact.description());
                ps.setString(5, artifact.createdAt());
                ps.executeUpdate();
                return withId(artifact, generatedId(ps));
            }
        }
    }

    @Override
    public WorkerSemanticHandoff insertHandoff(WorkerSemanticHandoff handoff) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT INTO workerSemanticHandoffs (sessionId, workCompleted, decisionsAndReasons, "
                    + "artifactsAndLocations, unresolvedProblems, requiredUserDecisions, recommendedNextAction, "
                    + "successorTask, successorContextAndFiles, successorAvailableTools, "
                    + "successorConstraintsAndPermissions, successorDefinitionOfDone, "
                    + "successorEscalationBehavior, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, handoff.sessionId());
                ps.setString(2, handoff.workCompleted());
                ps.setString(3, handoff.decisionsAndReasons());
                ps.setString(4, handoff.artifactsAndLocations());
                ps.setString(5, handoff.unresolvedProblems());
                ps.setString(6, handoff.requiredUserDecisions());
                ps.setString(7, handoff.recommendedNextAction());
                ps.setString(8, handoff.successorTask());
                ps.setString(9, handoff.successorContextAndFiles());
                ps.setString(10, handoff.successorAvailableTools());
                ps.setString(11, handoff.successorConstraintsAndPermissions());
                ps.setString(12, handoff.successorDefinitionOfDone());
                ps.setString(13, handoff.successorEscalationBehavior());
                ps.setString(14, handoff.createdAt());
                ps.executeUpdate();
                return withId(handoff, generatedId(ps));
            }
        }
    }

    @Override
    public Optional<WorkerAssignment> findAssignment(long assignmentId) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(selectAssignment() + " WHERE id = ?")) {
                ps.setLong(1, assignmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readAssignment(rs)) : Optional.empty();
                }
            }
        }
    }

    @Override
    public Optional<WorkerSession> findSession(long sessionId) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(selectSession() + " WHERE id = ?")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readSession(rs)) : Optional.empty();
                }
            }
        }
    }

    @Override
    public Optional<WorkerSession> findSessionByAssignment(long assignmentId) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(selectSession()
                    + " WHERE assignmentId = ? ORDER BY id LIMIT 1")) {
                ps.setLong(1, assignmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readSession(rs)) : Optional.empty();
                }
            }
        }
    }

    @Override
    public List<WorkerLifecycleEvent> findEvents(long sessionId) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(selectEvent() + " WHERE sessionId = ? ORDER BY id")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<WorkerLifecycleEvent> events = new ArrayList<>();
                    while (rs.next()) {
                        events.add(readEvent(rs));
                    }
                    return events;
                }
            }
        }
    }

    @Override
    public List<WorkerArtifact> findArtifacts(long sessionId) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(selectArtifact() + " WHERE sessionId = ? ORDER BY id")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<WorkerArtifact> artifacts = new ArrayList<>();
                    while (rs.next()) {
                        artifacts.add(readArtifact(rs));
                    }
                    return artifacts;
                }
            }
        }
    }

    @Override
    public Optional<WorkerSemanticHandoff> findHandoff(long sessionId) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(selectHandoff() + " WHERE sessionId = ?")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readHandoff(rs)) : Optional.empty();
                }
            }
        }
    }

    @Override
    public List<WorkerAssignment> findSuccessorAssignments(long predecessorSessionId) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(selectAssignment()
                    + " WHERE predecessorSessionId = ? ORDER BY id")) {
                ps.setLong(1, predecessorSessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<WorkerAssignment> assignments = new ArrayList<>();
                    while (rs.next()) {
                        assignments.add(readAssignment(rs));
                    }
                    return assignments;
                }
            }
        }
    }

    private static WorkerAssignment withId(WorkerAssignment assignment, long id) {
        return new WorkerAssignment(id, assignment.predecessorSessionId(), assignment.task(),
                assignment.contextAndFiles(), assignment.availableTools(), assignment.constraintsAndPermissions(),
                assignment.definitionOfDone(), assignment.escalationBehavior(), assignment.createdAt());
    }

    private static WorkerSession withId(WorkerSession session, long id) {
        return new WorkerSession(id, session.assignmentId(), session.workerIdentity(), session.lifecycleState(),
                session.createdAt(), session.updatedAt());
    }

    private static WorkerLifecycleEvent withId(WorkerLifecycleEvent event, long id) {
        return new WorkerLifecycleEvent(id, event.sessionId(), event.fromState(), event.toState(), event.question(),
                event.reason(), event.partialWork(), event.createdAt());
    }

    private static WorkerArtifact withId(WorkerArtifact artifact, long id) {
        return new WorkerArtifact(id, artifact.sessionId(), artifact.label(), artifact.location(),
                artifact.description(), artifact.createdAt());
    }

    private static WorkerSemanticHandoff withId(WorkerSemanticHandoff handoff, long id) {
        return new WorkerSemanticHandoff(id, handoff.sessionId(), handoff.workCompleted(),
                handoff.decisionsAndReasons(), handoff.artifactsAndLocations(), handoff.unresolvedProblems(),
                handoff.requiredUserDecisions(), handoff.recommendedNextAction(), handoff.successorTask(),
                handoff.successorContextAndFiles(), handoff.successorAvailableTools(),
                handoff.successorConstraintsAndPermissions(), handoff.successorDefinitionOfDone(),
                handoff.successorEscalationBehavior(), handoff.createdAt());
    }

    private static long generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }

    private static void setLongOrNull(PreparedStatement ps, int parameter, Long value) throws SQLException {
        if (value == null) {
            ps.setObject(parameter, null);
        } else {
            ps.setLong(parameter, value);
        }
    }

    private static String selectAssignment() {
        return "SELECT id, predecessorSessionId, task, contextAndFiles, availableTools, "
                + "constraintsAndPermissions, definitionOfDone, escalationBehavior, createdAt "
                + "FROM workerAssignments";
    }

    private static String selectSession() {
        return "SELECT id, assignmentId, workerIdentity, lifecycleState, createdAt, updatedAt FROM workerSessions";
    }

    private static String selectEvent() {
        return "SELECT id, sessionId, fromState, toState, question, reason, partialWork, createdAt "
                + "FROM workerLifecycleEvents";
    }

    private static String selectArtifact() {
        return "SELECT id, sessionId, label, location, description, createdAt FROM workerArtifacts";
    }

    private static String selectHandoff() {
        return "SELECT id, sessionId, workCompleted, decisionsAndReasons, artifactsAndLocations, "
                + "unresolvedProblems, requiredUserDecisions, recommendedNextAction, successorTask, "
                + "successorContextAndFiles, successorAvailableTools, successorConstraintsAndPermissions, "
                + "successorDefinitionOfDone, successorEscalationBehavior, createdAt FROM workerSemanticHandoffs";
    }

    private static WorkerAssignment readAssignment(ResultSet rs) throws SQLException {
        long predecessor = rs.getLong("predecessorSessionId");
        return new WorkerAssignment(rs.getLong("id"), rs.wasNull() ? null : predecessor,
                rs.getString("task"), rs.getString("contextAndFiles"), rs.getString("availableTools"),
                rs.getString("constraintsAndPermissions"), rs.getString("definitionOfDone"),
                rs.getString("escalationBehavior"), rs.getString("createdAt"));
    }

    private static WorkerSession readSession(ResultSet rs) throws SQLException {
        return new WorkerSession(rs.getLong("id"), rs.getLong("assignmentId"), rs.getString("workerIdentity"),
                WorkerLifecycleState.valueOf(rs.getString("lifecycleState")),
                rs.getString("createdAt"), rs.getString("updatedAt"));
    }

    private static WorkerLifecycleEvent readEvent(ResultSet rs) throws SQLException {
        return new WorkerLifecycleEvent(rs.getLong("id"), rs.getLong("sessionId"),
                WorkerLifecycleState.valueOf(rs.getString("fromState")),
                WorkerLifecycleState.valueOf(rs.getString("toState")), rs.getString("question"),
                rs.getString("reason"), rs.getString("partialWork"), rs.getString("createdAt"));
    }

    private static WorkerArtifact readArtifact(ResultSet rs) throws SQLException {
        return new WorkerArtifact(rs.getLong("id"), rs.getLong("sessionId"), rs.getString("label"),
                rs.getString("location"), rs.getString("description"), rs.getString("createdAt"));
    }

    private static WorkerSemanticHandoff readHandoff(ResultSet rs) throws SQLException {
        return new WorkerSemanticHandoff(rs.getLong("id"), rs.getLong("sessionId"),
                rs.getString("workCompleted"), rs.getString("decisionsAndReasons"),
                rs.getString("artifactsAndLocations"), rs.getString("unresolvedProblems"),
                rs.getString("requiredUserDecisions"), rs.getString("recommendedNextAction"),
                rs.getString("successorTask"), rs.getString("successorContextAndFiles"),
                rs.getString("successorAvailableTools"), rs.getString("successorConstraintsAndPermissions"),
                rs.getString("successorDefinitionOfDone"), rs.getString("successorEscalationBehavior"),
                rs.getString("createdAt"));
    }
}
