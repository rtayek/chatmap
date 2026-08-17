package chatmap.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chatmap.application.port.ai.AiRequest;
import chatmap.application.port.ai.AiResponse;
import chatmap.application.port.ai.BackendId;
import chatmap.application.port.ai.AiProvider;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.ProviderId;
import chatmap.domain.Source;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.service.PromptService;

final class DefaultAiBackendsTest {

    @Test
    void everyModelTargetHasAConfiguredProvider() {
        Map<ProviderId, AiProvider> providers = DefaultAiBackends.providers(noopExecutor(), Duration.ofSeconds(1));

        for (ModelTarget target : ModelTarget.values()) {
            assertNotNull(providers.get(target.providerId()), target.id());
        }
    }

    @Test
    void promptServiceFailsEarlyWhenAReferencedProviderIsMissing() {
        EnumMap<ProviderId, AiProvider> incomplete = new EnumMap<>(
                DefaultAiBackends.providers(noopExecutor(), Duration.ofSeconds(1)));
        incomplete.remove(ModelTarget.claude.providerId());

        assertThrows(IllegalStateException.class,
                () -> new PromptService(incomplete, null, java.time.Clock.systemUTC(), java.nio.file.Path.of(".")));
    }

    @Test
    void summaryBackendIsScopedToClaudeTarget() {
        EnumMap<ProviderId, AiProvider> providers = new EnumMap<>(ProviderId.class);
        providers.put(ProviderId.claudeCli, new CapturingProvider());

        var backend = DefaultAiBackends.summaryBackend(providers);
        AiResponse response = backend.ask(AiRequest.of("summarize"));

        assertEquals("summary", response.text());
        assertEquals(ModelTarget.claude.id(), response.targetId());
        assertEquals(Source.claudeCliPrompt, backend.source());
    }

    private static CommandExecutor noopExecutor() {
        return request -> {
            throw new AssertionError("unexpected command execution");
        };
    }

    private static final class CapturingProvider implements AiProvider {
        @Override
        public AiResponse execute(ModelTarget target, AiRequest request) {
            return new AiResponse("summary", new BackendId("test"), Duration.ZERO, target, null);
        }

        @Override
        public java.util.Set<chatmap.application.port.ai.AiCapability> capabilities(ModelTarget target) {
            return java.util.Set.of();
        }
    }
}
