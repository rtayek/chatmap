package chatmap.app;

import java.time.Duration;

import chatmap.infrastructure.ai.ClaudeCliBackend;
import chatmap.infrastructure.ai.DefaultAiBackends;
import chatmap.infrastructure.provider.DefaultChatProviders;
import chatmap.app.ServiceGraph.Integrations;

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
