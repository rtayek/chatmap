package chatmap.application.port.command;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record CommandRequest(
        List<String> command,
        String standardInput,
        Duration timeout,
        Path workingDirectory,
        java.io.PrintStream stdoutTee,
        java.io.PrintStream stderrTee,
        Path stdoutPath,
        Path stderrPath
) {
    public CommandRequest {
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        command = List.copyOf(command);
        standardInput = standardInput == null ? "" : standardInput;
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    /** Runs in the current process's own working directory, same as before this field existed. */
    public CommandRequest(List<String> command, String standardInput, Duration timeout) {
        this(command, standardInput, timeout, null, null, null, null, null);
    }

    public CommandRequest(List<String> command, String standardInput, Duration timeout, Path workingDirectory) {
        this(command, standardInput, timeout, workingDirectory, null, null, null, null);
    }

    public CommandRequest(List<String> command, String standardInput, Duration timeout, Path workingDirectory,
            java.io.PrintStream stdoutTee, java.io.PrintStream stderrTee) {
        this(command, standardInput, timeout, workingDirectory, stdoutTee, stderrTee, null, null);
    }

    public CommandRequest withOutputPaths(Path stdoutPath, Path stderrPath) {
        return new CommandRequest(command, standardInput, timeout, workingDirectory, stdoutTee, stderrTee,
                stdoutPath, stderrPath);
    }
}
