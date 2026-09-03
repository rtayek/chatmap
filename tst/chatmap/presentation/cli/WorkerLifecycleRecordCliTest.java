package chatmap.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.presentation.cli.CliBootstrap.CliContext;
import ch.qos.logback.classic.LoggerContext;

class WorkerLifecycleRecordCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void reopensAndDisplaysRecordedSession() throws Exception {
        Path home = tempDirectory.resolve("home");
        long sessionId;
        try (CliContext context = CliBootstrap.open(new String[] {"--home", home.toString()})) {
            WorkerLifecycleService lifecycle = context.services().workerLifecycleService();
            var assignment = lifecycle.createAssignment(new WorkerAssignmentInput(
                    "Inspect recorded A2A work",
                    "Raw A2A task snapshots",
                    "ChatMap worker lifecycle query",
                    "Read only",
                    "Display the persisted record",
                    "Report missing information"));
            var session = lifecycle.createSession(assignment.id(), "a2a:test-worker");
            sessionId = session.id();
            lifecycle.transition(sessionId, WorkerLifecycleState.WORKING);
            lifecycle.addArtifact(sessionId, "A2A task snapshot", "file:///snapshot.json",
                    "taskId=task-1 contextId=context-1 state=WORKING");
            lifecycle.transition(sessionId, WorkerLifecycleState.COMPLETED);
        }

        WorkerLifecycleRecord record = WorkerLifecycleRecordCli.execute(
                CliBootstrap.parse(new String[] {"--home", home.toString(), Long.toString(sessionId)}));
        String output = WorkerLifecycleRecordCli.format(record);
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
        }

        assertEquals(WorkerLifecycleState.COMPLETED, record.session().lifecycleState());
        assertEquals(2, record.events().size());
        assertEquals(1, record.artifacts().size());
        assertTrue(output.contains("WORKER LIFECYCLE RECORD"));
        assertTrue(output.contains("QUEUED -> WORKING"));
        assertTrue(output.contains("WORKING -> COMPLETED"));
        assertTrue(output.contains("file:///snapshot.json"));
    }
}
