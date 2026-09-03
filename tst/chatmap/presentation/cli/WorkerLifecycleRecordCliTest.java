package chatmap.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.TransactionRunner;
import chatmap.infrastructure.persistence.sqlite.WorkerLifecycleRepository;

class WorkerLifecycleRecordCliTest {
    @Test
    void formatsPersistedSessionRecord() throws Exception {
        WorkerLifecycleRecord record;
        try (Connection connection = new Database("jdbc:sqlite::memory:").openAndInitialize()) {
            WorkerLifecycleService lifecycle = new WorkerLifecycleService(
                    new WorkerLifecycleRepository(connection),
                    new TransactionRunner(connection),
                    Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
            var assignment = lifecycle.createAssignment(new WorkerAssignmentInput(
                    "Inspect recorded A2A work",
                    "Raw A2A task snapshots",
                    "ChatMap worker lifecycle query",
                    "Read only",
                    "Display the persisted record",
                    "Report missing information"));
            var session = lifecycle.createSession(assignment.id(), "a2a:test-worker");
            lifecycle.transition(session.id(), WorkerLifecycleState.WORKING);
            lifecycle.addArtifact(session.id(), "A2A task snapshot", "file:///snapshot.json",
                    "taskId=task-1 contextId=context-1 state=WORKING");
            lifecycle.transition(session.id(), WorkerLifecycleState.COMPLETED);
            record = lifecycle.record(session.id());
        }

        String output = WorkerLifecycleRecordCli.format(record);

        assertEquals(WorkerLifecycleState.COMPLETED, record.session().lifecycleState());
        assertEquals(2, record.events().size());
        assertEquals(1, record.artifacts().size());
        assertTrue(output.contains("WORKER LIFECYCLE RECORD"));
        assertTrue(output.contains("QUEUED -> WORKING"));
        assertTrue(output.contains("WORKING -> COMPLETED"));
        assertTrue(output.contains("file:///snapshot.json"));
    }
}
