package chatmap.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;
/** Opt-in smoke test for the locally installed Ollama service and model. */
@Tag("live")
final class OllamaProviderLiveTest {

    @Test
    void realOllamaAnswersThroughTheProvider() {
        assumeTrue(Boolean.getBoolean("chatmap.live.llm"),
                "Enable with -PliveLlm=true");

        String targetId = System.getProperty("chatmap.live.ollama.target", "ollama-glm4");
        ModelTarget target = ModelTarget.require(targetId);
        assumeTrue(target.channel() == chatmap.application.port.llm.Channel.ollama,
                "Target must be an Ollama model: " + targetId);

        OllamaProvider provider = new OllamaProvider();
        LlmResponse response = provider.execute(target,
                LlmRequest.of("Reply with exactly the word OK."));

        assertNotNull(response);
        assertFalse(response.text().isBlank(), "Ollama returned an empty response");
        assertTrue(response.text().toUpperCase(java.util.Locale.ROOT).contains("OK"),
                "Ollama response did not contain OK: " + response.text());
        assertTrue(response.channel().name().equals("ollama"));
        assertTrue(response.providerModelName().equals(target.providerModelName()));
    }
}
