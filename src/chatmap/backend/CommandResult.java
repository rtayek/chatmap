package chatmap.backend;

import java.time.Duration;
import java.util.Objects;

public record CommandResult(
        int exitCode,
        String standardOutput,
        String standardError,
        Duration duration,
        boolean timedOut
) {
    public CommandResult {
        Objects.requireNonNull(standardOutput, "standardOutput");
        Objects.requireNonNull(standardError, "standardError");
        Objects.requireNonNull(duration, "duration");
    }
}
