package chatmap.a2a.experiment;

import java.util.List;
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

public final class ExperimentClient {
    public static void main(String[] arguments) throws Exception {
        String request = arguments.length == 0 ? "complete:hello" : arguments[0];
        AgentCard agentCard = A2ACardResolver.builder()
                .baseUrl(SERVER_URL)
                .build()
                .getAgentCard();

        System.out.println("AGENT CARD");
        printJson(agentCard);
        System.out.println("REQUEST");
        System.out.println(request);

        List<BiConsumer<ClientEvent, AgentCard>> consumers =
                List.of(ExperimentClient::printEvent);

        try (Client client = Client.builder(agentCard)
                .addConsumers(consumers)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .build()) {
            client.sendMessage(A2A.toUserMessage(request));
        }
    }

    private static void printEvent(ClientEvent event, AgentCard agentCard) {
        System.out.println("EVENT " + event.getClass().getSimpleName());

        if (event instanceof TaskEvent taskEvent) {
            printJson(taskEvent.getTask());
        } else if (event instanceof TaskUpdateEvent updateEvent) {
            printJson(updateEvent.getTask());
        } else if (event instanceof MessageEvent messageEvent) {
            printJson(messageEvent.getMessage());
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

    private ExperimentClient() {
    }
}
