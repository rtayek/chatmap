package chatmap.presentation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import chatmap.application.port.llm.ModelTarget;
import chatmap.application.service.ConversationContext;
import chatmap.application.service.ModelRoute;
import chatmap.application.service.ProjectContext;
import chatmap.application.service.PromptClassification;
import chatmap.application.service.PromptResult;
import chatmap.application.service.PromptRoutingResult;
import chatmap.domain.PromptClassificationLevel;
import chatmap.domain.PromptClassificationReason;
import chatmap.domain.PromptRouteRecord;

class PromptResultDisplayTest {

    @Test
    void formatsClassificationWithConfidenceAndReasons() {
        PromptRoutingResult result = result(Optional.of("qwen2.5:7b"), "session-1");

        assertEquals("Classification: LIGHTWEIGHT (0.73; explanation, localScope)",
                PromptResultDisplay.classificationText(result));
    }

    @Test
    void formatsActualRouteTargetProviderModelAndSession() {
        PromptRoutingResult result = result(Optional.of("qwen2.5:7b"), "session-1");

        assertEquals("Route: ollama -> Ollama Qwen 2.5 7B [ollama-qwen2.5-7b], "
                + "model qwen2.5:7b, provider Fake backend, session session-1",
                PromptResultDisplay.routeText(result));
    }

    @Test
    void omitsOptionalModelAndSessionWhenProviderDoesNotReturnThem() {
        PromptRoutingResult result = result(Optional.empty(), null);

        assertEquals("Route: ollama -> Ollama Qwen 2.5 7B [ollama-qwen2.5-7b], provider Fake backend",
                PromptResultDisplay.routeText(result));
    }

    @Test
    void formatsSuccessStatusWithProjectAndConversation() {
        PromptRoutingResult result = result(Optional.empty(), null);

        assertEquals("Prompt stored for Foo / foo-current-task",
                PromptResultDisplay.successStatus(result));
    }

    private static PromptRoutingResult result(Optional<String> providerModelName, String sessionId) {
        PromptClassification classification = new PromptClassification(
                PromptClassificationLevel.LIGHTWEIGHT,
                0.734,
                List.of(PromptClassificationReason.explanation, PromptClassificationReason.localScope));
        ModelRoute route = new ModelRoute(PromptClassificationLevel.LIGHTWEIGHT, ModelTarget.ollamaQwen257b);
        PromptResult promptResult = new PromptResult("Fake backend", "response", Path.of("transcript.md"),
                "ollama", "ollama-qwen2.5-7b", providerModelName, sessionId, 42);
        return new PromptRoutingResult(
                ProjectContext.of("Foo", Path.of("C:/work/foo")),
                new ConversationContext("foo-current-task"),
                classification,
                route,
                promptResult,
                routeRecord(providerModelName, sessionId));
    }

    private static PromptRouteRecord routeRecord(Optional<String> providerModelName, String sessionId) {
        return new PromptRouteRecord(
                1,
                42,
                "chatmap",
                7,
                "Foo",
                "foo-current-task",
                Optional.of("C:/work/foo"),
                PromptClassificationLevel.LIGHTWEIGHT,
                0.734,
                List.of(PromptClassificationReason.explanation, PromptClassificationReason.localScope),
                "ollama",
                "ollama-qwen2.5-7b",
                providerModelName,
                Optional.ofNullable(sessionId),
                "SUCCEEDED",
                "2026-08-21T00:00:00Z");
    }
}
