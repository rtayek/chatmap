package chatmap.app;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import chatmap.application.port.ai.AiBackend;
import chatmap.application.port.provider.ChatProvider;
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
                chatProviders(),
                new ClaudeCliBackend(Duration.ofMinutes(3)),
                promptBackends());
    }

    public static List<ChatProvider> chatProviders() {
        return DefaultChatProviders.ordered();
    }

    public static Map<String, AiBackend> promptBackends() {
        return DefaultAiBackends.defaults();
    }
}
