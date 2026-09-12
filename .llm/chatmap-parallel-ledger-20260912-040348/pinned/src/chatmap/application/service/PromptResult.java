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
        String channelId,
        String targetId,
        Optional<String> providerModelName,
        String providerSessionId,
        long chatId
) {
    public PromptResult {
        Objects.requireNonNull(backendLabel, "backendLabel");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(providerModelName, "providerModelName");
    }

    public PromptResult(String backendLabel, String response, Path transcriptPath) {
        this(backendLabel, response, transcriptPath, "unknown", "unknown", Optional.empty(), null, 0);
    }

    /** The transcript file, when one could be written. */
    public Optional<Path> transcript() {
        return Optional.ofNullable(transcriptPath);
    }

    public Optional<String> sessionId() {
        return Optional.ofNullable(providerSessionId).filter(value -> !value.isBlank());
    }
}
