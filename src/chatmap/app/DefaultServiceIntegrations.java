package chatmap.app;

import java.util.List;
import java.util.Map;

import chatmap.application.port.ai.AiBackend;
import chatmap.application.port.ai.AiProvider;
import chatmap.application.port.ai.ProviderId;
import chatmap.application.port.provider.ChatProvider;
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
                DefaultAiBackends.summaryBackend(),
                promptProviders());
    }

    public static List<ChatProvider> chatProviders() {
        return DefaultChatProviders.ordered();
    }

    public static Map<String, AiBackend> promptBackends() {
        return DefaultAiBackends.defaults();
    }

    public static Map<ProviderId, AiProvider> promptProviders() {
        return DefaultAiBackends.providers();
    }
}
