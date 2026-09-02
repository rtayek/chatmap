package chatmap.a2a.experiment;

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

public final class ContinuationClient {
    public static void main(String[] arguments) throws Exception {
        String answer = arguments.length == 0 ? "continued hello" : arguments[0];
        AgentCard agentCard = A2ACardResolver.builder()
                .baseUrl(SERVER_URL)
                .build()
                .getAgentCard();
        AtomicReference<Task> observedTask = new AtomicReference<>();
        List<BiConsumer<ClientEvent, AgentCard>> consumers =
                List.of((event, card) -> observe(event, observedTask));

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
    }

    private static void observe(ClientEvent event, AtomicReference<Task> observedTask) {
        System.out.println("EVENT " + event.getClass().getSimpleName());

        if (event instanceof TaskEvent taskEvent) {
            observedTask.set(taskEvent.getTask());
            printJson(taskEvent.getTask());
        } else if (event instanceof TaskUpdateEvent updateEvent) {
            observedTask.set(updateEvent.getTask());
            printJson(updateEvent.getTask());
        } else if (event instanceof MessageEvent messageEvent) {
            printJson(messageEvent.getMessage());
        }
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

    private static void printJson(Object value) {
        try {
            System.out.println(JsonUtil.toJson(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode A2A event as JSON", exception);
        }
    }

    private static final String SERVER_URL = "http://localhost:9999";

    private ContinuationClient() {
    }
}
