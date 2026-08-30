package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.application.service.WorkerLifecycleService.DecisionRequest;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.application.service.WorkerLifecycleService.WorkerSemanticHandoffInput;
import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleChain;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSession;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.TransactionRunner;
import chatmap.infrastructure.persistence.sqlite.WorkerLifecycleRepository;

class WorkerLifecycleServiceTest {
    private Connection conn;
    private WorkerLifecycleService service;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        service = new WorkerLifecycleService(new WorkerLifecycleRepository(conn), new TransactionRunner(conn),
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void createsCompleteLifecycleAndRejectsInvalidTransitions() throws Exception {
        WorkerAssignment assignment = service.createAssignment(assignmentInput("Build lifecycle"));
        WorkerSession session = service.createSession(assignment.id(), "codex");

        IllegalStateException invalid = assertThrows(IllegalStateException.class,
                () -> service.transition(session.id(), WorkerLifecycleState.COMPLETED));
        assertTrue(invalid.getMessage().contains("QUEUED -> COMPLETED"));

        service.transition(session.id(), WorkerLifecycleState.WORKING);
        service.transition(session.id(), WorkerLifecycleState.WAITING_FOR_DECISION, new DecisionRequest(
                "Continue?", "Need user scope decision", "Partial implementation"));
        service.transition(session.id(), WorkerLifecycleState.WORKING);
        service.addArtifact(session.id(), "patch", "file://patch.diff", "implementation patch");
        service.transition(session.id(), WorkerLifecycleState.COMPLETED);
        service.storeHandoff(session.id(), handoffInput("Review lifecycle"));
        service.transition(session.id(), WorkerLifecycleState.RETIRED);
        WorkerAssignment successor = service.createSuccessorAssignment(session.id(), assignmentInput("Review lifecycle"));
        WorkerSession successorSession = service.createSession(successor.id(), "reviewer");
        service.transition(successorSession.id(), WorkerLifecycleState.WORKING);

        WorkerLifecycleChain chain = service.chainFrom(session.id());

        assertEquals(2, chain.records().size());
        assertEquals(WorkerLifecycleState.RETIRED, chain.records().get(0).session().lifecycleState());
        assertEquals(5, chain.records().get(0).events().size());
        assertEquals(1, chain.records().get(0).artifacts().size());
        assertTrue(chain.records().get(0).handoff().isPresent());
        assertEquals("Review lifecycle", chain.records().get(1).assignment().task());
    }

    @Test
    void waitingForDecisionRequiresQuestionReasonAndPartialWork() throws Exception {
        WorkerAssignment assignment = service.createAssignment(assignmentInput("Build lifecycle"));
        WorkerSession session = service.createSession(assignment.id(), "codex");
        service.transition(session.id(), WorkerLifecycleState.WORKING);

        assertThrows(IllegalArgumentException.class,
                () -> service.transition(session.id(), WorkerLifecycleState.WAITING_FOR_DECISION));
    }

    private static WorkerAssignmentInput assignmentInput(String task) {
        return new WorkerAssignmentInput(task, "src and tst", "Gradle and JUnit",
                "No broad harness", "Tests pass", "Stop safely and report blockers");
    }

    private static WorkerSemanticHandoffInput handoffInput(String successorTask) {
        return new WorkerSemanticHandoffInput("Implemented lifecycle", "Kept scope narrow",
                "patch at file://patch.diff", "None", "None", "Run review", successorTask,
                "chain records", "Gradle", "No merge", "Review result", "Report blocker");
    }
}
