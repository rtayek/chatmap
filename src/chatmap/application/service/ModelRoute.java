package chatmap.application.service;

import java.util.Objects;

import chatmap.application.port.llm.ModelTarget;
import chatmap.domain.PromptClassificationLevel;

/** Selected target for a classified prompt. */
public record ModelRoute(
        PromptClassificationLevel classificationLevel,
        ModelTarget target) {

    public ModelRoute {
        Objects.requireNonNull(classificationLevel, "classificationLevel");
        Objects.requireNonNull(target, "target");
    }
}
