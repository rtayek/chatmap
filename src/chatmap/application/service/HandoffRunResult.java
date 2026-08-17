package chatmap.application.service;

import java.nio.file.Path;
import java.util.Objects;

/** Outcome of processing one handoff markdown file found in the inbox. */
public record HandoffRunResult(
        Path sourceFile,
        String projectKey,
        Outcome outcome,
        String detail,
        boolean pushPending) {

    public HandoffRunResult {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(projectKey, "projectKey");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(detail, "detail");
    }

    public enum Outcome {
        success, failure, partialFailure
    }
}
