package chatmap.app;

import java.time.Duration;

import chatmap.backend.ai.ClaudeCliBackend;
import chatmap.backend.ai.DefaultAiBackends;
import chatmap.backend.providers.DefaultChatProviders;
import chatmap.service.ServiceGraph.Integrations;

/** Default optional integrations used by interactive entry points. */
public final class DefaultServiceIntegrations {

    private DefaultServiceIntegrations() {
    }

    public static Integrations create() {
        return new Integrations(
                DefaultChatProviders.ordered(),
                new ClaudeCliBackend(Duration.ofMinutes(3)),
                DefaultAiBackends.defaults());
    }
}
