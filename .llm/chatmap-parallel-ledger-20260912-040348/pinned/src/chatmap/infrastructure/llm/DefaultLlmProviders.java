package chatmap.infrastructure.llm;

import chatmap.application.port.llm.LlmBackend;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.Channel;

import chatmap.infrastructure.command.ProcessRunner;

import chatmap.application.port.command.CommandExecutor;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Factory for default LLM backends available for prompt execution across CLI and UI entry points.
 */
public final class DefaultLlmProviders {

    private DefaultLlmProviders() {
    }

    /** Primary prompt provider wiring: one provider implementation per provider/protocol family. */
    public static Map<Channel, LlmProvider> providers() {
        return providers(new ProcessRunner(), Duration.ofMinutes(3));
    }

    public static Map<Channel, LlmProvider> providers(CommandExecutor executor, Duration timeout) {
        EnumMap<Channel, LlmProvider> providers = new EnumMap<>(Channel.class);
        providers.put(Channel.claudeCli, new ClaudeCliProvider(executor, timeout));
        providers.put(Channel.codexCli, new CodexCliProvider(executor, timeout));
        providers.put(Channel.antigravityCli, new AntigravityCliProvider(executor, timeout));
        providers.put(Channel.ollama, new OllamaProvider());
        providers.put(Channel.jshell, new JShellBackend());
        return Collections.unmodifiableMap(providers);
    }

    public static LlmBackend summaryBackend() {
        return summaryBackend(providers());
    }

    static LlmBackend summaryBackend(Map<Channel, LlmProvider> providers) {
        ModelTarget target = ModelTarget.claude;
        LlmProvider provider = providers.get(target.providerId());
        if (provider == null) {
            throw new IllegalStateException("No LLM provider configured for " + target.providerId());
        }
        return new LlmBackend() {
            @Override
            public LlmResponse ask(LlmRequest request) {
                return provider.execute(target, request);
            }

            @Override
            public chatmap.domain.Source source() {
                return target.source();
            }

            @Override
            public java.util.List<String> listSessions() {
                return provider.listSessions(target);
            }
        };
    }
}
