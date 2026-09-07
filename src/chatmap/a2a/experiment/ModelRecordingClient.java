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
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;

import chatmap.a2a.experiment.A2aTaskRecorder.Recording;
import chatmap.application.service.WorkerLifecycleService;
import chatmap.application.service.WorkerLifecycleService.WorkerAssignmentInput;
import chatmap.domain.WorkerLifecycleRecord;
import chatmap.presentation.cli.CliBootstrap;
import chatmap.presentation.cli.CliBootstrap.CliContext;

/** Records one real model-backed A2A task in an isolated ChatMap ledger. */
public final class ModelRecordingClient {
    public static void main(String[] arguments) throws Exception {
        String request = arguments.length == 0 ? DEFAULT_REQUEST : arguments[0];
        AgentCard agentCard = A2ACardResolver.builder()
                .baseUrl(SERVER_URL)
                .build()
                .getAgentCard();

        Path recordingHome = Files.createTempDirectory("chatmap-a2a-model-recording-");
        Path artifactDirectory = recordingHome.resolve("artifacts");

        try (CliContext context = CliBootstrap.open(new String[] {"--home", recordingHome.toString()})) {
            WorkerLifecycleService lifecycle = context.services().workerLifecycleService();
            A2aTaskRecorder recorder = new A2aTaskRecorder(lifecycle, artifactDirectory);
            Recording recording = recorder.begin(
                    assignmentInput(request),
                    "a2a:" + agentCard.name());
            AtomicReference<Task> observedTask = new AtomicReference<>();
            List<BiConsumer<ClientEvent, AgentCard>> consumers =
                    List.of((event, card) -> observe(
                            event,
                            observedTask,
                            recorder,
                            recording.sessionId()));

            System.out.println("MODEL REQUEST");
            System.out.println(request);
            try (Client client = Client.builder(agentCard)
                    .addConsumers(consumers)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .build()) {
                client.sendMessage(A2A.toUserMessage(request));
            }

            Task completedTask = requireCompleted(observedTask.get());
            WorkerLifecycleRecord record = lifecycle.record(recording.sessionId());
            printRecording(
                    completedTask,
                    record,
                    context,
                    artifactDirectory);
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

    private static WorkerAssignmentInput assignmentInput(String request) {
        return new WorkerAssignmentInput(
                request,
                "Preserve the externally visible A2A task and local-model result",
                "Official A2A Java JSON-RPC client and local Ollama worker",
                "Record only protocol-visible data in an isolated temporary ChatMap home",
                "Complete the A2A task and durably preserve its text artifact",
                "Return unresolved decisions through the caller chain");
    }

    private static Task requireCompleted(Task task) {
        if (task == null) {
            throw new IllegalStateException("No A2A task was received");
        }
        if (task.status().state() != TaskState.TASK_STATE_COMPLETED) {
            throw new IllegalStateException(
                    "Expected a completed model task but received " + task.status().state());
        }
        return task;
    }

    private static void printRecording(Task task, WorkerLifecycleRecord record,
            CliContext context, Path artifactDirectory) {
        System.out.println("MODEL RECORDING PROVEN");
        System.out.println("taskId=" + task.id());
        System.out.println("contextId=" + task.contextId());
        System.out.println("home=" + context.paths().homeDirectory());
        System.out.println("database=" + context.paths().databasePath());
        System.out.println("artifactDirectory=" + artifactDirectory);
        System.out.println("sessionId=" + record.session().id());
        System.out.println("state=" + record.session().lifecycleState());
        System.out.println("events=" + record.events().size());
        System.out.println("artifacts=" + record.artifacts().size());
    }

    private static String toJson(Object value) {
        try {
            return JsonUtil.toJson(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode A2A event as JSON", exception);
        }
    }

    private static final String SERVER_URL = "http://localhost:9999";
    private static final String DEFAULT_REQUEST =
            "Explain durable task history in two short sentences.";

    private ModelRecordingClient() {
    }
}
