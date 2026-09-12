package chatmap.application.service;

import java.util.List;
import java.util.Objects;

import chatmap.domain.PromptClassificationLevel;
import chatmap.domain.PromptClassificationReason;

/** Deterministic classifier result with auditable confidence and reason codes. */
public record PromptClassification(
        PromptClassificationLevel level,
        double confidence,
        List<PromptClassificationReason> reasons) {

    public PromptClassification {
        Objects.requireNonNull(level, "level");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }
}
