package chatmap.infrastructure.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleEvent;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSession;

class WorkerLifecycleRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsLifecycleAcrossReopen() throws Exception {
        Path db = tempDir.resolve("chatmap.db");
        long assignmentId;
        long sessionId;
        try (Connection conn = new Database("jdbc:sqlite:" + db).openAndInitialize()) {
            WorkerLifecycleRepository workers = new WorkerLifecycleRepository(conn);
            WorkerAssignment assignment = workers.insertAssignment(new WorkerAssignment(0, null,
                    "Task", "Files", "Tools", "Constraints", "Done", "Escalate", "2026-08-26T00:00:00Z"));
            WorkerSession session = workers.insertSession(new WorkerSession(0, assignment.id(), "worker",
                    WorkerLifecycleState.QUEUED, "2026-08-26T00:00:00Z", "2026-08-26T00:00:00Z"));
            workers.insertEvent(new WorkerLifecycleEvent(0, session.id(), WorkerLifecycleState.QUEUED,
                    WorkerLifecycleState.WORKING, null, null, null, "2026-08-26T00:00:01Z"));
            workers.updateSessionState(session.id(), WorkerLifecycleState.WORKING, "2026-08-26T00:00:01Z");
            assignmentId = assignment.id();
            sessionId = session.id();
        }

        try (Connection conn = new Database("jdbc:sqlite:" + db).openAndInitialize()) {
            WorkerLifecycleRepository workers = new WorkerLifecycleRepository(conn);

            assertEquals("Task", workers.findAssignment(assignmentId).orElseThrow().task());
            assertEquals(WorkerLifecycleState.WORKING,
                    workers.findSession(sessionId).orElseThrow().lifecycleState());
            assertEquals(1, workers.findEvents(sessionId).size());
        }
    }

    @Test
    void schemaCreatesWorkerLifecycleTables() throws Exception {
        try (Connection conn = new Database("jdbc:sqlite::memory:").openAndInitialize()) {
            assertTrue(tableExists(conn, "workerAssignments"));
            assertTrue(tableExists(conn, "workerSessions"));
            assertTrue(tableExists(conn, "workerLifecycleEvents"));
            assertTrue(tableExists(conn, "workerArtifacts"));
            assertTrue(tableExists(conn, "workerSemanticHandoffs"));
        }
    }

    private static boolean tableExists(Connection conn, String name) throws Exception {
        try (var ps = conn.prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, name);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
