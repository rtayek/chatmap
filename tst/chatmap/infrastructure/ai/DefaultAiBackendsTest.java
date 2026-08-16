package chatmap.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chatmap.application.port.ai.AiProvider;
import chatmap.application.port.ai.ModelTarget;
import chatmap.application.port.ai.ProviderId;
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

    private static CommandExecutor noopExecutor() {
        return request -> {
            throw new AssertionError("unexpected command execution");
        };
    }
}
