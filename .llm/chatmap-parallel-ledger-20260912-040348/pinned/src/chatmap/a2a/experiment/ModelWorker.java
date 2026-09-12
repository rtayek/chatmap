package chatmap.a2a.experiment;

import java.util.Objects;

import chatmap.application.port.llm.LlmBackendException;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.LlmResponse;
import chatmap.application.port.llm.ModelTarget;

/** Bounded A2A worker backed by one configured ChatMap model provider. */
final class ModelWorker {
    private static final String SYSTEM_PROMPT =
            "You are a bounded A2A worker. Answer the user's request directly "
                    + "and return only the text that should become the task artifact.";

    private final LlmProvider provider;
    private final ModelTarget target;

    ModelWorker(LlmProvider provider, ModelTarget target) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.target = Objects.requireNonNull(target, "target");
    }

    FakeWorker.Result execute(String request) {
        String prompt = request == null ? "" : request.trim();
        if (prompt.isEmpty()) {
            return failed("The model worker requires a nonblank prompt");
        }

        try {
            LlmResponse response = provider.execute(
                    target,
                    LlmRequest.withSystemPrompt(prompt, SYSTEM_PROMPT));
            return new FakeWorker.Result(FakeWorker.Status.COMPLETED, response.text());
        } catch (LlmBackendException failure) {
            return failed("The model worker failed: " + failure.getMessage());
        }
    }

    private static FakeWorker.Result failed(String message) {
        return new FakeWorker.Result(FakeWorker.Status.FAILED, message);
    }
}
