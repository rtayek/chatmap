package chatmap.app;

import java.util.List;
import java.util.Map;

import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.ProviderId;
import chatmap.application.port.provider.ChatProvider;
import chatmap.infrastructure.llm.DefaultLlmProviders;
import chatmap.infrastructure.provider.DefaultChatProviders;
import chatmap.app.ServiceGraph.Integrations;

/** Default optional integrations used by interactive entry points. */
public final class DefaultServiceIntegrations {

    private DefaultServiceIntegrations() {
    }

    public static Integrations create() {
        return new Integrations(
                chatProviders(),
                DefaultLlmProviders.summaryBackend(),
                promptProviders());
    }

    public static List<ChatProvider> chatProviders() {
        return DefaultChatProviders.ordered();
    }

    public static Map<ProviderId, LlmProvider> promptProviders() {
        return DefaultLlmProviders.providers();
    }
}
