package chatmap.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.ProviderId;
import chatmap.domain.Source;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.service.PromptService;

final class DefaultLlmProvidersTest {

    @Test
    void everyModelTargetHasAConfiguredProvider() {
        Map<ProviderId, LlmProvider> providers = DefaultLlmProviders.providers(noopExecutor(), Duration.ofSeconds(1));

        for (ModelTarget target : ModelTarget.values()) {
            assertNotNull(providers.get(target.providerId()), target.id());
        }
    }

    @Test
    void promptServiceFailsEarlyWhenAReferencedProviderIsMissing() {
        EnumMap<ProviderId, LlmProvider> incomplete = new EnumMap<>(
                DefaultLlmProviders.providers(noopExecutor(), Duration.ofSeconds(1)));
        incomplete.remove(ModelTarget.claude.providerId());

        assertThrows(IllegalStateException.class,
                () -> new PromptService(incomplete, null, java.time.Clock.systemUTC(), java.nio.file.Path.of(".")));
    }

    @Test
    void summaryBackendIsScopedToClaudeTarget() {
        EnumMap<ProviderId, LlmProvider> providers = new EnumMap<>(ProviderId.class);
        providers.put(ProviderId.claudeCli, new CapturingProvider());

        var backend = DefaultLlmProviders.summaryBackend(providers);
        LlmResponse response = backend.ask(LlmRequest.of("summarize"));

        assertEquals("summary", response.text());
        assertEquals(ModelTarget.claude.id(), response.targetId());
        assertEquals(Source.claudeCliPrompt, backend.source());
    }

    private static CommandExecutor noopExecutor() {
        return request -> {
            throw new AssertionError("unexpected command execution");
        };
    }

    private static final class CapturingProvider implements LlmProvider {
        @Override
        public LlmResponse execute(ModelTarget target, LlmRequest request) {
            return new LlmResponse("summary", new BackendId("test"), Duration.ZERO, target, null);
        }

        @Override
        public java.util.Set<chatmap.application.port.llm.LlmCapability> capabilities(ModelTarget target) {
            return java.util.Set.of();
        }
    }
}
