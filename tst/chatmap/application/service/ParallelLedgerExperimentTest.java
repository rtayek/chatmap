package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.application.service.WorkerLifecycleService.WorkerSemanticHandoffInput;
import chatmap.domain.WorkerArtifact;
import chatmap.domain.WorkerAssignment;
import chatmap.domain.WorkerLifecycleChain;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.domain.WorkerSession;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.TransactionRunner;
import chatmap.infrastructure.persistence.sqlite.WorkerLifecycleRepository;

class ParallelLedgerExperimentTest {
    private Connection conn;
    private WorkerLifecycleService service;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        service = new WorkerLifecycleService(new WorkerLifecycleRepository(conn), new TransactionRunner(conn),
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void executeParallelRunAndRecordLifecycle() throws Exception {
        // 1. Coordinator
        WorkerAssignment coordAssignment = service.createAssignment(new WorkerAssignmentInput(
                "Coordinate parallel subagent experiment",
                "repository at 22761162ea4b0e93a658bcc9d516b6b606e72235",
                "invoke_subagent, git",
                "Read-only workers, no shared state",
                "Synthesis assignment created and stored",
                "Stop safely and report blockers"));
        WorkerSession coordSession = service.createSession(coordAssignment.id(), "coordinator");
        service.transition(coordSession.id(), WorkerLifecycleState.WORKING);

        // 2. Worker 1: Lifecycle Evidence
        WorkerAssignment w1Assignment = service.createSuccessorAssignment(coordSession.id(), new WorkerAssignmentInput(
                "Inspect whether recorded lifecycle matches actual run",
                "same commit as coordinator",
                "read-only tools",
                "Do not modify code",
                "Markdown artifact with findings",
                "Report missing state"));
        WorkerSession w1Session = service.createSession(w1Assignment.id(), "worker1-lifecycle");
        service.transition(w1Session.id(), WorkerLifecycleState.WORKING);
        WorkerArtifact w1Artifact = service.addArtifact(w1Session.id(), "lifecycle-findings", "file://.llm/handoffs/worker1-lifecycle.md", "Findings on missing state");
        service.transition(w1Session.id(), WorkerLifecycleState.COMPLETED);

        // 3. Worker 2: Operations Evidence
        WorkerAssignment w2Assignment = service.createSuccessorAssignment(coordSession.id(), new WorkerAssignmentInput(
                "Examine isolation, timing, failure handling, artifact paths",
                "same commit as coordinator",
                "read-only tools",
                "Do not modify code",
                "Markdown artifact with findings",
                "Report procedural vs enforced safeguards"));
        WorkerSession w2Session = service.createSession(w2Assignment.id(), "worker2-operations");
        service.transition(w2Session.id(), WorkerLifecycleState.WORKING);
        WorkerArtifact w2Artifact = service.addArtifact(w2Session.id(), "operations-findings", "file://.llm/handoffs/worker2-operations.md", "Findings on isolation");
        service.transition(w2Session.id(), WorkerLifecycleState.COMPLETED);

        // 4. Worker 3: Independent Verifier
        WorkerAssignment w3Assignment = service.createSuccessorAssignment(coordSession.id(), new WorkerAssignmentInput(
                "Check evidence requirements of the other two assignments",
                "same commit as coordinator",
                "read-only tools",
                "Do not modify code",
                "Markdown artifact with findings",
                "Report contradictions or omissions"));
        WorkerSession w3Session = service.createSession(w3Assignment.id(), "worker3-verifier");
        service.transition(w3Session.id(), WorkerLifecycleState.WORKING);
        WorkerArtifact w3Artifact = service.addArtifact(w3Session.id(), "verifier-findings", "file://.llm/handoffs/worker3-verifier.md", "Findings on contradictions");
        service.transition(w3Session.id(), WorkerLifecycleState.COMPLETED);

        // 5. Synthesis Worker
        String synthesisContext = String.format("Child sessions: [%d, %d, %d], Artifacts: [%s, %s, %s]",
                w1Session.id(), w2Session.id(), w3Session.id(), w1Artifact.location(), w2Artifact.location(), w3Artifact.location());
        WorkerAssignment synthAssignment = service.createSuccessorAssignment(coordSession.id(), new WorkerAssignmentInput(
                "Synthesize findings from parallel workers",
                synthesisContext,
                "read-only tools",
                "Preserve disagreements, do not force consensus",
                "Synthesis artifact",
                "Report unresolved conflicts"));
        WorkerSession synthSession = service.createSession(synthAssignment.id(), "synthesis-worker");
        service.transition(synthSession.id(), WorkerLifecycleState.WORKING);
        WorkerArtifact synthArtifact = service.addArtifact(synthSession.id(), "synthesis-report", "file://.llm/handoffs/synthesis-report.md", "Synthesis of all 3 workers");
        service.transition(synthSession.id(), WorkerLifecycleState.COMPLETED);

        // 6. Complete Coordinator
        service.storeHandoff(coordSession.id(), new WorkerSemanticHandoffInput(
                "Parallel subagents launched and synthesized",
                "Used separate child sessions to record parallel execution",
                synthArtifact.location(),
                "No native DAG support yet, synthesis contextAndFiles used",
                "None",
                "Review synthesis",
                "N/A", "N/A", "N/A", "N/A", "N/A", "N/A"));
        service.transition(coordSession.id(), WorkerLifecycleState.COMPLETED);
        service.transition(coordSession.id(), WorkerLifecycleState.RETIRED);

        // 7. Verify chain
        WorkerLifecycleChain chain = service.chainFrom(coordSession.id());
        assertEquals(5, chain.records().size());
        
        Optional<WorkerLifecycleRecord> coordRecord = chain.records().stream()
                .filter(r -> r.session().id() == coordSession.id())
                .findFirst();
        assertTrue(coordRecord.isPresent());
        assertEquals(4, coordRecord.get().successorAssignments().size());
    }
}
