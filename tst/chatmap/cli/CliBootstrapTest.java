package chatmap.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.config.LoggingBootstrap;

final class CliBootstrapTest {

    @TempDir
    Path tempDir;

    private String originalLogDirectory;
    private PrintStream originalError;

    @BeforeEach
    void rememberProcessState() {
        originalLogDirectory = System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);
        originalError = System.err;
    }

    @AfterEach
    void restoreProcessState() {
        System.setErr(originalError);
        LoggingBootstrap.initializeTemporaryFallback();
        restoreProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY, originalLogDirectory);
    }

    @Test
    void parseResolvesExplicitHomeAndPreservesToolArguments() {
        Path home = tempDir.resolve("home");

        ParsedArguments parsed = CliBootstrap.parse(
                new String[] { "--home", home.toString(), "archive.zip" });

        assertEquals(home.toAbsolutePath().normalize(), parsed.paths().homeDirectory());
        assertEquals(java.util.List.of("archive.zip"), parsed.remainingArgs());
        assertFalse(Files.exists(parsed.paths().databasePath()));
    }

    @Test
    void openCreatesTheSelectedDatabaseAndSharedServices() throws Exception {
        Path home = tempDir.resolve("home");

        try (CliBootstrap.CliContext context = CliBootstrap.open(
                new String[] { "--home", home.toString() })) {
            assertEquals(home.toAbsolutePath().normalize(), context.paths().homeDirectory());
            assertTrue(Files.isRegularFile(context.paths().databasePath()));
            assertTrue(Files.isDirectory(context.paths().logsDirectory()));
            assertTrue(context.services().chats().findAll().isEmpty());
        }
    }

    @Test
    void parseOrExitPrintsErrorAndUsageAndRequestsExitOne() {
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        ExitRequested exit = assertThrows(ExitRequested.class,
                () -> CliBootstrap.parseOrExit(
                        new String[] { "--home" }, "Usage: tool [--home <directory>]",
                        status -> {
                            throw new ExitRequested(status);
                        }));

        String error = errorBytes.toString(StandardCharsets.UTF_8);
        assertEquals(1, exit.status());
        assertTrue(error.contains("Missing value for --home"));
        assertTrue(error.contains("Usage: tool [--home <directory>]"));
    }

    @Test
    void exitWithUsagePrintsUsageAndRequestsExitOne() {
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        ExitRequested exit = assertThrows(ExitRequested.class,
                () -> CliBootstrap.exitWithUsage("Usage: tool", status -> {
                    throw new ExitRequested(status);
                }));

        assertEquals(1, exit.status());
        assertEquals("Usage: tool" + System.lineSeparator(), errorBytes.toString(StandardCharsets.UTF_8));
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class ExitRequested extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;

        ExitRequested(int status) {
            this.status = status;
        }

        int status() {
            return status;
        }
    }
}
