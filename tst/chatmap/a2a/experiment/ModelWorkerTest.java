package chatmap.a2a.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

import chatmap.application.port.llm.BackendId;
import chatmap.application.port.llm.LlmBackendStartupException;
import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;

class ModelWorkerTest {
    @Test
    void returnsModelTextAsCompletedArtifactContent() {
        StubProvider provider = StubProvider.returning("model answer");
        ModelWorker worker = new ModelWorker(provider, ModelTarget.ollamaGlm4);

        FakeWorker.Result result = worker.execute("  explain this  ");

        assertEquals(FakeWorker.Status.COMPLETED, result.status());
        assertEquals("model answer", result.text());
        assertEquals("explain this", provider.request.prompt());
        assertTrue(provider.request.systemPrompt().isPresent());
    }

    @Test
    void reportsProviderFailureAsFailedTaskContent() {
        StubProvider provider = StubProvider.failing();
        ModelWorker worker = new ModelWorker(provider, ModelTarget.ollamaGlm4);

        FakeWorker.Result result = worker.execute("hello");

        assertEquals(FakeWorker.Status.FAILED, result.status());
        assertTrue(result.text().contains("offline"));
    }

    @Test
    void rejectsBlankPromptWithoutCallingProvider() {
        StubProvider provider = StubProvider.returning("unused");
        ModelWorker worker = new ModelWorker(provider, ModelTarget.ollamaGlm4);

        FakeWorker.Result result = worker.execute("   ");

        assertEquals(FakeWorker.Status.FAILED, result.status());
        assertEquals("The model worker requires a nonblank prompt", result.text());
        assertNull(provider.request);
    }

    private static final class StubProvider implements LlmProvider {
        private final String responseText;
        private final boolean fail;
        private LlmRequest request;

        private StubProvider(String responseText, boolean fail) {
            this.responseText = responseText;
            this.fail = fail;
        }

        static StubProvider returning(String responseText) {
            return new StubProvider(responseText, false);
        }

        static StubProvider failing() {
            return new StubProvider(null, true);
        }

        @Override
        public LlmResponse execute(ModelTarget target, LlmRequest request) {
            this.request = request;
            if (fail) {
                throw new LlmBackendStartupException(
                        "offline",
                        new BackendId("test"),
                        new IllegalStateException("offline"));
            }
            return new LlmResponse(
                    responseText,
                    new BackendId("test"),
                    Duration.ZERO,
                    target,
                    null);
        }

        @Override
        public Set<LlmCapability> capabilities(ModelTarget target) {
            return Set.of();
        }
    }
}
