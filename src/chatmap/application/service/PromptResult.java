package chatmap.application.service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of a prompt run. The exchange is recorded in the database; the
 * transcript file is best-effort debug output and may be absent.
 */
public record PromptResult(
        String backendLabel,
        String response,
        Path transcriptPath,
        String targetId,
        String providerModelName,
        String providerSessionId
) {
    public PromptResult {
        Objects.requireNonNull(backendLabel, "backendLabel");
        Objects.requireNonNull(response, "response");
    }

    public PromptResult(String backendLabel, String response, Path transcriptPath) {
        this(backendLabel, response, transcriptPath, backendLabel, "default", null);
    }

    /** The transcript file, when one could be written. */
    public Optional<Path> transcript() {
        return Optional.ofNullable(transcriptPath);
    }

    public Optional<String> sessionId() {
        return Optional.ofNullable(providerSessionId).filter(value -> !value.isBlank());
    }
}
