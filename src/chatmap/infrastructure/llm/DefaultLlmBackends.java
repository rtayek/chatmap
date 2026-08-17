package chatmap.infrastructure.llm;

import chatmap.application.port.llm.LlmBackend;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.ProviderId;

import chatmap.infrastructure.command.CommandRunner;

import chatmap.application.port.command.CommandExecutor;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Factory for default LLM backends available for prompt execution across CLI and UI entry points.
 */
public final class DefaultLlmBackends {

    private DefaultLlmBackends() {
    }

    /** Primary prompt provider wiring: one provider implementation per provider/protocol family. */
    public static Map<ProviderId, LlmProvider> providers() {
        return providers(new CommandRunner(), Duration.ofMinutes(3));
    }

    public static Map<ProviderId, LlmProvider> providers(CommandExecutor executor, Duration timeout) {
        EnumMap<ProviderId, LlmProvider> providers = new EnumMap<>(ProviderId.class);
        providers.put(ProviderId.claudeCli, new ClaudeCliProvider(executor, timeout));
        providers.put(ProviderId.codexCli, new CodexCliProvider(executor, timeout));
        providers.put(ProviderId.antigravityCli, new AntigravityCliProvider(executor, timeout));
        providers.put(ProviderId.ollama, new OllamaCliProvider(executor, timeout));
        providers.put(ProviderId.jshell, new JShellBackend());
        return Collections.unmodifiableMap(providers);
    }

    public static LlmBackend summaryBackend() {
        return summaryBackend(providers());
    }

    static LlmBackend summaryBackend(Map<ProviderId, LlmProvider> providers) {
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
