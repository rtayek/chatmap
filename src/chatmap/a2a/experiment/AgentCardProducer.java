package chatmap.a2a.experiment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;

@ApplicationScoped
public class AgentCardProducer {
    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        AgentInterface jsonRpc = new AgentInterface(
                TransportProtocol.JSONRPC.asString(),
                "http://localhost:9999");

        AgentSkill deterministicWork = AgentSkill.builder()
                .id("deterministic-work")
                .name("Deterministic work")
                .description("Completes, requests input, or fails from explicit text commands")
                .tags(List.of("deterministic", "experiment"))
                .examples(List.of("complete:hello", "input-required", "fail"))
                .build();

        return AgentCard.builder()
                .name("ChatMap A2A Experiment")
                .description("A deterministic fake worker for studying A2A")
                .supportedInterfaces(List.of(jsonRpc))
                .version("0.1.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(deterministicWork))
                .build();
    }
}
