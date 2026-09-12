package chatmap.a2a.experiment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;

import chatmap.a2a.experiment.A2aTaskRecorder.Recording;
import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.presentation.cli.CliBootstrap;
import chatmap.presentation.cli.CliBootstrap.CliContext;

public final class ContinuationClient {
    public static void main(String[] arguments) throws Exception {
        String answer = arguments.length == 0 ? "continued hello" : arguments[0];
        Path recordingHome = Files.createTempDirectory("chatmap-a2a-recording-");
        Path artifactDirectory = recordingHome.resolve("artifacts");

        try (CliContext context = CliBootstrap.open(new String[] {"--home", recordingHome.toString()})) {
            WorkerLifecycleService lifecycle = context.services().workerLifecycleService();
            A2aTaskRecorder recorder = new A2aTaskRecorder(lifecycle, artifactDirectory);
            Recording recording = recorder.begin(assignmentInput(), "a2a:ChatMap A2A Experiment");

            AgentCard agentCard = A2ACardResolver.builder()
                    .baseUrl(SERVER_URL)
                    .build()
                    .getAgentCard();
            AtomicReference<Task> observedTask = new AtomicReference<>();
            List<BiConsumer<ClientEvent, AgentCard>> consumers =
                    List.of((event, card) -> observe(event, observedTask, recorder, recording.sessionId()));

            try (Client client = Client.builder(agentCard)
                    .addConsumers(consumers)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .build()) {
                System.out.println("FIRST REQUEST");
                System.out.println("input-required");
                client.sendMessage(A2A.toUserMessage("input-required"));

                Task waitingTask = requireState(
                        observedTask.get(),
                        TaskState.TASK_STATE_INPUT_REQUIRED,
                        "first response");

                observedTask.set(null);
                Message continuation = A2A.createUserTextMessage(
                        "complete:" + answer,
                        waitingTask.contextId(),
                        waitingTask.id());

                System.out.println("CONTINUATION REQUEST");
                System.out.println("taskId=" + continuation.taskId());
                System.out.println("contextId=" + continuation.contextId());
                System.out.println("text=complete:" + answer);
                client.sendMessage(continuation);

                Task completedTask = requireState(
                        observedTask.get(),
                        TaskState.TASK_STATE_COMPLETED,
                        "continuation response");
                requireSameIdentity(waitingTask, completedTask);

                System.out.println("CONTINUATION PROVEN");
                System.out.println("taskId=" + completedTask.id());
                System.out.println("contextId=" + completedTask.contextId());
            }

            printRecording(lifecycle.record(recording.sessionId()), context, artifactDirectory);
        }
    }

    private static void observe(ClientEvent event, AtomicReference<Task> observedTask,
            A2aTaskRecorder recorder, long sessionId) {
        System.out.println("EVENT " + event.getClass().getSimpleName());

        if (event instanceof TaskEvent taskEvent) {
            recordTask(taskEvent.getTask(), observedTask, recorder, sessionId);
        } else if (event instanceof TaskUpdateEvent updateEvent) {
            recordTask(updateEvent.getTask(), observedTask, recorder, sessionId);
        } else if (event instanceof MessageEvent messageEvent) {
            System.out.println(toJson(messageEvent.getMessage()));
        }
    }

    private static void recordTask(Task task, AtomicReference<Task> observedTask,
            A2aTaskRecorder recorder, long sessionId) {
        String taskJson = toJson(task);
        observedTask.set(task);
        System.out.println(taskJson);
        try {
            recorder.record(sessionId, taskJson);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to record A2A task in ChatMap", exception);
        }
    }

    private static WorkerAssignmentInput assignmentInput() {
        return new WorkerAssignmentInput(
                "Continue an A2A task after the remote worker requests input",
                "A2A task and context identities are preserved in raw task snapshot artifacts",
                "Official A2A Java JSON-RPC client",
                "Record only externally visible protocol data in an isolated temporary ChatMap home",
                "Preserve input-required and completed states, messages, history, and text artifacts",
                "Return unresolved decisions through the caller chain");
    }

    private static void printRecording(WorkerLifecycleRecord record, CliContext context, Path artifactDirectory) {
        System.out.println("CHATMAP RECORDING");
        System.out.println("home=" + context.paths().homeDirectory());
        System.out.println("database=" + context.paths().databasePath());
        System.out.println("artifactDirectory=" + artifactDirectory);
        System.out.println("sessionId=" + record.session().id());
        System.out.println("state=" + record.session().lifecycleState());
        System.out.println("events=" + record.events().size());
        System.out.println("artifacts=" + record.artifacts().size());
    }

    private static Task requireState(Task task, TaskState expectedState, String description) {
        if (task == null) {
            throw new IllegalStateException("No task received for " + description);
        }
        if (task.status().state() != expectedState) {
            throw new IllegalStateException(
                    "Expected " + expectedState + " for " + description
                            + " but received " + task.status().state());
        }
        return task;
    }

    private static void requireSameIdentity(Task waitingTask, Task completedTask) {
        if (!waitingTask.id().equals(completedTask.id())) {
            throw new IllegalStateException("Continuation created a different task");
        }
        if (!waitingTask.contextId().equals(completedTask.contextId())) {
            throw new IllegalStateException("Continuation created a different context");
        }
    }

    private static String toJson(Object value) {
        try {
            return JsonUtil.toJson(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode A2A event as JSON", exception);
        }
    }

    private static final String SERVER_URL = "http://localhost:9999";

    private ContinuationClient() {
    }
}
