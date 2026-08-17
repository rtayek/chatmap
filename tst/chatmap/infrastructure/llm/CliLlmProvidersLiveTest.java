package chatmap.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;
import chatmap.infrastructure.command.CommandRunner;

/** Opt-in smoke tests for installed authenticated CLI LLM providers. */
@Tag("live")
final class CliLlmProvidersLiveTest {

    @Test
    void realClaudeAnswersAndReturnsSessionIdentity() {
        assertLiveResponse(ModelTarget.claude,
                () -> new ClaudeCliProvider(new CommandRunner(), Duration.ofMinutes(2)),
                "Claude");
    }

    @Test
    void realCodexAnswersAndReturnsSessionIdentity() {
        assertLiveResponse(ModelTarget.codex,
                () -> new CodexCliProvider(new CommandRunner(), Duration.ofMinutes(2)),
                "Codex");
    }

    @Test
    void realAntigravityAnswersAndReturnsSessionIdentity() {
        assertLiveResponse(ModelTarget.agy,
                () -> new AntigravityCliProvider(new CommandRunner(), Duration.ofMinutes(2)),
                "Antigravity");
    }

    private static void assertLiveResponse(ModelTarget target, Supplier<LlmProvider> providerFactory,
            String providerName) {
        assumeTrue(Boolean.getBoolean("chatmap.live.llm"),
                "Enable with -PliveLlm=true");

        LlmResponse response = providerFactory.get().execute(target,
                LlmRequest.of("Reply with exactly the word OK."));

        assertNotNull(response);
        assertFalse(response.text().isBlank(), providerName + " returned an empty response");
        assertTrue(response.text().toUpperCase(Locale.ROOT).contains("OK"),
                providerName + " response did not contain OK: " + response.text());
        assertTrue(response.sessionId().isPresent(), providerName + " did not return a session ID");
        assertTrue(response.providerId() == target.providerId());
        assertTrue(response.providerModelName().equals(target.providerModelName()));
    }
}
