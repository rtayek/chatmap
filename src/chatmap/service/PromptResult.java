package chatmap.service;

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
        Path transcriptPath
) {
    public PromptResult {
        Objects.requireNonNull(backendLabel, "backendLabel");
        Objects.requireNonNull(response, "response");
    }

    /** The transcript file, when one could be written. */
    public Optional<Path> transcript() {
        return Optional.ofNullable(transcriptPath);
    }
}
