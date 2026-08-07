package chatmap.service;

import java.nio.file.Path;
import java.util.Objects;

public record PromptResult(
        String backendLabel,
        String response,
        Path transcriptPath
) {
    public PromptResult {
        Objects.requireNonNull(backendLabel, "backendLabel");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(transcriptPath, "transcriptPath");
    }
}
