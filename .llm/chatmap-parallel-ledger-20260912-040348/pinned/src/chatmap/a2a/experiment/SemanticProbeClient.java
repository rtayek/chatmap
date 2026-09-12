package chatmap.a2a.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

import chatmap.a2a.experiment.SemanticAcceptance.Evaluation;

/** Runs one model-backed A2A response through a deterministic semantic contract. */
public final class SemanticProbeClient {
    public static void main(String[] arguments) throws Exception {
        AgentCard agentCard = A2ACardResolver.builder()
                .baseUrl(SERVER_URL)
                .build()
                .getAgentCard();
        AtomicReference<Task> observedTask = new AtomicReference<>();
        List<BiConsumer<ClientEvent, AgentCard>> consumers =
                List.of((event, card) -> observe(event, observedTask));

        System.out.println("SEMANTIC PROBE REQUEST");
        System.out.println(SemanticAcceptance.prompt());
        try (Client client = Client.builder(agentCard)
                .addConsumers(consumers)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .build()) {
            client.sendMessage(A2A.toUserMessage(SemanticAcceptance.prompt()));
        }

        Task completedTask = requireCompleted(observedTask.get());
        String response = workerResult(completedTask);
        Evaluation evaluation = SemanticAcceptance.evaluate(response);

        System.out.println("SEMANTIC PROBE RESPONSE");
        System.out.println(response);
        if (evaluation.accepted()) {
            System.out.println("SEMANTIC CHECK PASSED");
            return;
        }

        System.out.println("SEMANTIC CHECK FAILED");
        for (String problem : evaluation.problems()) {
            System.out.println(problem);
        }
        throw new IllegalStateException("Model response did not satisfy the semantic contract");
    }

    private static void observe(ClientEvent event, AtomicReference<Task> observedTask) {
        System.out.println("EVENT " + event.getClass().getSimpleName());

        if (event instanceof TaskEvent taskEvent) {
            observeTask(taskEvent.getTask(), observedTask);
        } else if (event instanceof TaskUpdateEvent updateEvent) {
            observeTask(updateEvent.getTask(), observedTask);
        } else if (event instanceof MessageEvent messageEvent) {
            System.out.println(toJson(messageEvent.getMessage()));
        }
    }

    private static void observeTask(Task task, AtomicReference<Task> observedTask) {
        observedTask.set(task);
        System.out.println(toJson(task));
    }

    private static Task requireCompleted(Task task) {
        if (task == null) {
            throw new IllegalStateException("No A2A task was received");
        }
        if (task.status().state() != TaskState.TASK_STATE_COMPLETED) {
            throw new IllegalStateException(
                    "Expected a completed semantic-probe task but received "
                            + task.status().state());
        }
        return task;
    }

    private static String workerResult(Task task) {
        JsonObject taskJson = JsonParser.parseString(toJson(task)).getAsJsonObject();
        JsonElement artifactsElement = taskJson.get("artifacts");
        if (artifactsElement == null || !artifactsElement.isJsonArray()) {
            throw new IllegalStateException("Completed task has no artifacts");
        }

        for (JsonElement artifactElement : artifactsElement.getAsJsonArray()) {
            if (!artifactElement.isJsonObject()) {
                continue;
            }
            JsonObject artifact = artifactElement.getAsJsonObject();
            if (!"worker-result".equals(stringValue(artifact.get("artifactId")))) {
                continue;
            }
            String text = textParts(artifact.get("parts"));
            if (!text.isBlank()) {
                return text;
            }
        }
        throw new IllegalStateException("Completed task has no worker-result text artifact");
    }

    private static String textParts(JsonElement partsElement) {
        if (partsElement == null || !partsElement.isJsonArray()) {
            return "";
        }
        JsonArray parts = partsElement.getAsJsonArray();
        List<String> texts = new ArrayList<>();
        for (JsonElement partElement : parts) {
            if (!partElement.isJsonObject()) {
                continue;
            }
            String text = stringValue(partElement.getAsJsonObject().get("text"));
            if (!text.isBlank()) {
                texts.add(text);
            }
        }
        return String.join(System.lineSeparator(), texts);
    }

    private static String stringValue(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static String toJson(Object value) {
        try {
            return JsonUtil.toJson(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode A2A event as JSON", exception);
        }
    }

    private static final String SERVER_URL = "http://localhost:9999";

    private SemanticProbeClient() {
    }
}
