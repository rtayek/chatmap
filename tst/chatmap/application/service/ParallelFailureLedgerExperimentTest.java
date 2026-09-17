package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.service.WorkerLifecycleService.FailureReport;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.application.service.WorkerLifecycleService.WorkerSemanticHandoffInput;
import chatmap.domain.WorkerLifecycleChain;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSession;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.TransactionRunner;
import chatmap.infrastructure.persistence.sqlite.WorkerLifecycleRepository;

class ParallelFailureLedgerExperimentTest {

    @TempDir
    Path directory;

    @Test
    void failedSiblingAndSuccessfulSynthesisSurviveDatabaseReopen() throws Exception {
        Path worker1Report = write("worker-1.md", "lifecycle evidence");
        Path worker2Report = write("worker-2.md", "operations evidence");
        Path synthesisReport = write("synthesis.md", "two successful reports plus one recorded failure");
        Path database = directory.resolve("chatmap.db");

        long coordinatorSessionId;
        try (Connection connection = open(database)) {
            WorkerLifecycleService service = service(connection);
            WorkerSession coordinator = session(service, service.createAssignment(input(
                    "Coordinate bounded parallel run", "three sibling workers")).id(), "coordinator");
            coordinatorSessionId = coordinator.id();

            WorkerSession worker1 = successor(service, coordinator.id(), "worker-1:lifecycle");
            completeWithArtifact(service, worker1, "worker-1-report", worker1Report);

            WorkerSession worker2 = successor(service, coordinator.id(), "worker-2:operations");
            completeWithArtifact(service, worker2, "worker-2-report", worker2Report);

            WorkerSession failed = successor(service, coordinator.id(), "worker-3:deliberate-failure");
            service.transitionWithFailure(failed.id(), WorkerLifecycleState.FAILED, new FailureReport(
                    "Required input file is missing",
                    "Checked the exact required path; no artifact was produced and no findings were fabricated."));

            WorkerSession synthesis = successor(service, coordinator.id(), "coordinator-synthesis");
            completeWithArtifact(service, synthesis, "synthesis-report", synthesisReport);

            service.transition(coordinator.id(), WorkerLifecycleState.COMPLETED);
        }

        try (Connection connection = open(database)) {
            WorkerLifecycleChain chain = service(connection).chainFrom(coordinatorSessionId);

            assertEquals(5, chain.records().size());
            WorkerLifecycleRecord coordinator = find(chain, "coordinator");
            assertEquals(4, coordinator.successorAssignments().size());

            assertSuccessfulWorker(chain, "worker-1:lifecycle", worker1Report);
            assertSuccessfulWorker(chain, "worker-2:operations", worker2Report);

            WorkerLifecycleRecord failed = find(chain, "worker-3:deliberate-failure");
            assertEquals(WorkerLifecycleState.FAILED, failed.session().lifecycleState());
            assertTrue(failed.artifacts().isEmpty());
            assertTrue(failed.handoff().isEmpty());
            var failureEvent = failed.events().stream()
                    .filter(event -> event.toState() == WorkerLifecycleState.FAILED)
                    .findFirst().orElseThrow();
            assertEquals("Required input file is missing", failureEvent.reason());
            assertEquals("Checked the exact required path; no artifact was produced and no findings were fabricated.",
                    failureEvent.partialWork());

            WorkerLifecycleRecord synthesis = find(chain, "coordinator-synthesis");
            assertEquals(WorkerLifecycleState.COMPLETED, synthesis.session().lifecycleState());
            assertEquals(1, synthesis.artifacts().size());
            assertTrue(synthesis.handoff().isPresent());
            assertTrue(Files.isRegularFile(synthesisReport));
            assertFalse(Files.readString(synthesisReport).isBlank());
        }
    }

    private Path write(String name, String content) throws Exception {
        Path path = directory.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private static Connection open(Path database) throws Exception {
        return new Database("jdbc:sqlite:" + database).openAndInitialize();
    }

    private static WorkerLifecycleService service(Connection connection) {
        return new WorkerLifecycleService(new WorkerLifecycleRepository(connection),
                new TransactionRunner(connection),
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC));
    }

    private static WorkerSession session(WorkerLifecycleService service, long assignmentId, String identity)
            throws Exception {
        WorkerSession session = service.createSession(assignmentId, identity);
        return service.transition(session.id(), WorkerLifecycleState.WORKING);
    }

    private static WorkerSession successor(WorkerLifecycleService service, long coordinatorSessionId,
            String identity) throws Exception {
        long assignmentId = service.createSuccessorAssignment(coordinatorSessionId,
                input("Perform " + identity + " assignment", "bounded parallel experiment")).id();
        return session(service, assignmentId, identity);
    }

    private static void completeWithArtifact(WorkerLifecycleService service, WorkerSession session,
            String label, Path report) throws Exception {
        service.addArtifact(session.id(), label, report.toString(), Files.readString(report));
        service.transition(session.id(), WorkerLifecycleState.COMPLETED);
        service.storeHandoff(session.id(), new WorkerSemanticHandoffInput(
                "Completed assigned work", "Preserved observed outcome", label + " at " + report,
                "None", "None", "Return evidence to coordinator", "Synthesize evidence",
                report.toString(), "Read-only tools", "No schema changes", "Preserve all outcomes",
                "Report blockers without fabrication"));
    }

    private static WorkerAssignmentInput input(String task, String context) {
        return new WorkerAssignmentInput(task, context, "Read-only tools",
                "No scheduler or schema changes", "Preserve success and failure evidence",
                "Stop safely and preserve partial work");
    }

    private static WorkerLifecycleRecord find(WorkerLifecycleChain chain, String identity) {
        return chain.records().stream()
                .filter(record -> identity.equals(record.session().workerIdentity()))
                .findFirst().orElseThrow();
    }

    private static void assertSuccessfulWorker(WorkerLifecycleChain chain, String identity, Path report)
            throws Exception {
        WorkerLifecycleRecord worker = find(chain, identity);
        assertEquals(WorkerLifecycleState.COMPLETED, worker.session().lifecycleState());
        assertEquals(1, worker.artifacts().size());
        assertTrue(worker.handoff().isPresent());
        assertTrue(Files.isRegularFile(report));
        assertFalse(Files.readString(report).isBlank());
    }
}
