package chatmap.application.service;

import java.util.Objects;

import chatmap.application.port.llm.ModelTarget;
import chatmap.domain.PromptClassificationLevel;

/** Maps binary classification decisions to configured model targets. */
public final class PromptRouteSelector {

    private final ModelTarget lightweightTarget;
    private final ModelTarget monsterTarget;

    public PromptRouteSelector(ModelTarget lightweightTarget, ModelTarget monsterTarget) {
        this.lightweightTarget = Objects.requireNonNull(lightweightTarget, "lightweightTarget");
        this.monsterTarget = Objects.requireNonNull(monsterTarget, "monsterTarget");
    }

    public static PromptRouteSelector defaults() {
        return new PromptRouteSelector(ModelTarget.ollamaQwen257b, ModelTarget.claude);
    }

    public ModelRoute select(PromptClassification classification) {
        Objects.requireNonNull(classification, "classification");
        ModelTarget target = classification.level() == PromptClassificationLevel.LIGHTWEIGHT
                ? lightweightTarget
                : monsterTarget;
        return new ModelRoute(classification.level(), target);
    }
}
