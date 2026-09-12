package chatmap.application.port.command;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record CommandResult(
        int exitCode,
        String standardOutput,
        String standardError,
        Duration duration,
        boolean timedOut,
        boolean standardOutputTruncated,
        boolean standardErrorTruncated,
        Path standardOutputPath,
        Path standardErrorPath
) {
    public CommandResult {
        Objects.requireNonNull(standardOutput, "standardOutput");
        Objects.requireNonNull(standardError, "standardError");
        Objects.requireNonNull(duration, "duration");
    }

    public CommandResult(int exitCode, String standardOutput, String standardError, Duration duration,
            boolean timedOut) {
        this(exitCode, standardOutput, standardError, duration, timedOut, false, false, null, null);
    }
}
