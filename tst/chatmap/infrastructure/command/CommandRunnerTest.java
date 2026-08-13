package chatmap.infrastructure.command;

import chatmap.application.port.command.CommandExecutionException;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

final class CommandRunnerTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void capturesExitCodeStandardStreamsInputAndDuration() {
        CommandRequest request = new CommandRequest(
                probeCommand("normal"), "input text", Duration.ofSeconds(5));

        CommandResult result = new CommandRunner().run(request);

        assertEquals(7, result.exitCode());
        assertEquals("stdout:input text", result.standardOutput());
        assertEquals("stderr", result.standardError());
        assertFalse(result.timedOut());
        assertFalse(result.duration().isNegative());
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void timeoutTerminatesParentAndDescendant(@TempDir Path tempDir) throws Exception {
        Path descendantPidFile = tempDir.resolve("descendant.pid");
        CommandRequest request = new CommandRequest(
                probeCommand("spawn", descendantPidFile.toString()), "", Duration.ofMillis(500));

        long descendantPid = -1;
        try {
            CommandResult result = new CommandRunner().run(request);
            descendantPid = readPid(descendantPidFile);

            assertTrue(result.timedOut());
            assertEquals(-1, result.exitCode());
            assertTrue(waitUntilNotAlive(descendantPid, Duration.ofSeconds(3)),
                    "descendant process should not survive the timed-out parent");
        } finally {
            if (descendantPid < 0 && Files.isRegularFile(descendantPidFile)) {
                descendantPid = readPid(descendantPidFile);
            }
            if (descendantPid >= 0) {
                ProcessHandle.of(descendantPid).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }

    @Test
    void missingExecutableThrowsCommandExecutionException() {
        CommandRequest request = new CommandRequest(
                List.of("chatmap-command-that-does-not-exist-7f53317d"), "", Duration.ofSeconds(1));

        CommandExecutionException failure = assertThrows(
                CommandExecutionException.class, () -> new CommandRunner().run(request));

        assertTrue(failure.getMessage().contains("Could not start command"));
    }

    @Test
    void requestValidatesAndCopiesItsClosedInputs() {
        List<String> mutableCommand = new ArrayList<>(List.of("command"));
        CommandRequest request = new CommandRequest(mutableCommand, null, Duration.ofSeconds(1));
        mutableCommand.add("changed");

        assertEquals(List.of("command"), request.command());
        assertEquals("", request.standardInput());
        assertThrows(IllegalArgumentException.class,
                () -> new CommandRequest(List.of(), "", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new CommandRequest(List.of("command"), "", Duration.ZERO));
        assertThrows(NullPointerException.class,
                () -> new CommandRequest(List.of("command"), "", null));
    }

    @Test
    void resultRejectsNullOutputAndDuration() {
        assertThrows(NullPointerException.class,
                () -> new CommandResult(0, null, "", Duration.ZERO, false));
        assertThrows(NullPointerException.class,
                () -> new CommandResult(0, "", null, Duration.ZERO, false));
        assertThrows(NullPointerException.class,
                () -> new CommandResult(0, "", "", null, false));
    }

    private static List<String> probeCommand(String... arguments) {
        return CommandRunnerProbe.command(arguments);
    }

    private static long readPid(Path pidFile) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!Files.isRegularFile(pidFile) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        return Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8));
    }

    private static boolean waitUntilNotAlive(long pid, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
                && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        return !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}

final class CommandRunnerProbe {

    private CommandRunnerProbe() {
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "normal" -> {
                String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
                System.out.print("stdout:" + input);
                System.err.print("stderr");
                System.exit(7);
            }
            case "spawn" -> {
                Process descendant = new ProcessBuilder(command("sleep"))
                        .inheritIO()
                        .start();
                Files.writeString(Path.of(args[1]), Long.toString(descendant.pid()), StandardCharsets.UTF_8);
                Thread.sleep(Duration.ofMinutes(1));
            }
            case "sleep" -> Thread.sleep(Duration.ofMinutes(1));
            default -> throw new IllegalArgumentException("Unknown probe mode: " + args[0]);
        }
    }

    static List<String> command(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-cp");
        command.add(probeClasspath());
        command.add(CommandRunnerProbe.class.getName());
        command.addAll(List.of(arguments));
        return List.copyOf(command);
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String probeClasspath() {
        try {
            return Path.of(CommandRunnerProbe.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .toString();
        } catch (java.net.URISyntaxException exception) {
            throw new AssertionError("Could not resolve command-runner probe classpath", exception);
        }
    }
}
