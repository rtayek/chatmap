package chatmap.a2a.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.a2a.experiment.A2aTaskRecorder.Recording;
import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.domain.WorkerLifecycleState;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.TransactionRunner;
import chatmap.infrastructure.persistence.sqlite.WorkerLifecycleRepository;

class A2aTaskRecorderTest {
    @TempDir
    Path artifactDirectory;

    private Connection connection;
    private A2aTaskRecorder recorder;
    private WorkerLifecycleService lifecycle;

    @BeforeEach
    void setUp() throws Exception {
        connection = new Database("jdbc:sqlite::memory:").openAndInitialize();
        lifecycle = new WorkerLifecycleService(
                new WorkerLifecycleRepository(connection),
                new TransactionRunner(connection),
                Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
        recorder = new A2aTaskRecorder(lifecycle, artifactDirectory);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void recordsCompletedTaskAndInlineArtifact() throws Exception {
        Recording recording = recorder.begin(assignmentInput("complete:hello"), "a2a:fake-worker");

        WorkerLifecycleRecord record = recorder.record(recording.sessionId(), """
                {
                  "id":"task-complete",
                  "contextId":"context-complete",
                  "status":{"state":"TASK_STATE_COMPLETED"},
                  "artifacts":[{
                    "artifactId":"fake-worker-result",
                    "name":"Fake worker result",
                    "parts":[{"text":"hello"}]
                  }]
                }
                """);

        assertEquals(WorkerLifecycleState.COMPLETED, record.session().lifecycleState());
        assertEquals(2, record.events().size());
        assertEquals(2, record.artifacts().size());
        assertEquals("hello", readArtifact(record, 1));
        assertTrue(readArtifact(record, 0).contains("\"task-complete\""));
    }

    @Test
    void recordsInputRequiredAndSameSessionContinuation() throws Exception {
        Recording recording = recorder.begin(assignmentInput("input-required"), "a2a:fake-worker");

        WorkerLifecycleRecord waiting = recorder.record(recording.sessionId(), """
                {
                  "id":"task-continue",
                  "contextId":"context-continue",
                  "status":{
                    "state":"TASK_STATE_INPUT_REQUIRED",
                    "message":{"parts":[{"text":"Please provide text after complete:"}]}
                  },
                  "artifacts":[]
                }
                """);

        assertEquals(WorkerLifecycleState.WAITING_FOR_DECISION, waiting.session().lifecycleState());
        assertEquals("Please provide text after complete:", waiting.events().get(1).question());

        WorkerLifecycleRecord completed = recorder.record(recording.sessionId(), """
                {
                  "id":"task-continue",
                  "contextId":"context-continue",
                  "status":{"state":"TASK_STATE_COMPLETED"},
                  "artifacts":[{
                    "artifactId":"fake-worker-result",
                    "name":"Fake worker result",
                    "parts":[{"text":"continued hello"}]
                  }]
                }
                """);

        assertEquals(WorkerLifecycleState.COMPLETED, completed.session().lifecycleState());
        assertEquals(4, completed.events().size());
        assertEquals(3, completed.artifacts().size());
        assertEquals("continued hello", readArtifact(completed, 2));
    }

    @Test
    void recordsFailedTaskAndPreservesReasonInSnapshot() throws Exception {
        Recording recording = recorder.begin(assignmentInput("fail"), "a2a:fake-worker");

        WorkerLifecycleRecord record = recorder.record(recording.sessionId(), """
                {
                  "id":"task-failed",
                  "contextId":"context-failed",
                  "status":{
                    "state":"TASK_STATE_FAILED",
                    "message":{"parts":[{"text":"The fake worker failed as requested"}]}
                  },
                  "artifacts":[]
                }
                """);

        assertEquals(WorkerLifecycleState.FAILED, record.session().lifecycleState());
        assertEquals(2, record.events().size());
        assertEquals(1, record.artifacts().size());
        assertTrue(readArtifact(record, 0).contains("The fake worker failed as requested"));
    }

    private static String readArtifact(WorkerLifecycleRecord record, int index) throws Exception {
        URI location = URI.create(record.artifacts().get(index).location());
        return Files.readString(Path.of(location));
    }

    private static WorkerAssignmentInput assignmentInput(String task) {
        return new WorkerAssignmentInput(
                task,
                "A2A task and context identity are preserved in task snapshot artifacts",
                "A2A JSON-RPC",
                "Record only externally visible protocol data",
                "Lifecycle and artifacts are durably recorded",
                "Return unresolved decisions through the caller chain");
    }
}
