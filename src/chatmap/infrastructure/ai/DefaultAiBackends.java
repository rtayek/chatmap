package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiBackend;
import chatmap.application.port.ai.AiProvider;
import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.ProviderId;

import chatmap.infrastructure.command.CommandRunner;

import chatmap.application.port.command.CommandExecutor;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Factory for default AI backends available for prompt execution across CLI and UI entry points.
 */
public final class DefaultAiBackends {

    private DefaultAiBackends() {
    }

    public static Map<String, AiBackend> defaults() {
        return legacyBackends(new CommandRunner(), Duration.ofMinutes(3));
    }

    /** Primary prompt provider wiring: one provider implementation per provider/protocol family. */
    public static Map<ProviderId, AiProvider> providers() {
        return providers(new CommandRunner(), Duration.ofMinutes(3));
    }

    public static Map<ProviderId, AiProvider> providers(CommandExecutor executor, Duration timeout) {
        EnumMap<ProviderId, AiProvider> providers = new EnumMap<>(ProviderId.class);
        providers.put(ProviderId.claudeCli, new ClaudeCliProvider(executor, timeout));
        providers.put(ProviderId.codexCli, new CodexCliProvider(executor, timeout));
        providers.put(ProviderId.antigravityCli, new AntigravityCliProvider(executor, timeout));
        providers.put(ProviderId.ollama, new OllamaCliProvider(executor, timeout));
        providers.put(ProviderId.jshell, new JShellBackend());
        return Collections.unmodifiableMap(providers);
    }

    public static AiBackend summaryBackend() {
        return targetBackend(ModelTarget.claude, providers());
    }

    public static Map<String, AiBackend> defaults(CommandExecutor executor, Duration timeout) {
        return legacyBackends(executor, timeout);
    }

    public static Map<String, AiBackend> legacyBackends(CommandExecutor executor, Duration timeout) {
        Map<ProviderId, AiProvider> providers = providers(executor, timeout);
        return Map.of(
                "claude", targetBackend(ModelTarget.claude, providers),
                "codex", targetBackend(ModelTarget.codex, providers),
                "agy", targetBackend(ModelTarget.agy, providers),
                "ollama", targetBackend(ModelTarget.ollama, providers),
                "jshell", targetBackend(ModelTarget.jshell, providers)
        );
    }

    private static AiBackend targetBackend(ModelTarget target, Map<ProviderId, AiProvider> providers) {
        AiProvider provider = providers.get(target.providerId());
        if (provider == null) {
            throw new IllegalStateException("No AI provider configured for " + target.providerId());
        }
        return new AiBackend() {
            @Override
            public AiResponse ask(AiRequest request) {
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
