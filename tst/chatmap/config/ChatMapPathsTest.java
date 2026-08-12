package chatmap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ChatMapPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void explicitHomeWinsOverEnvironmentAndDefaults() throws Exception {
        Path explicit = tempDir.resolve("explicit home");
        Path envHome = tempDir.resolve("env-home");
        Path current = tempDir.resolve("repo");
        Path legacy = tempDir.resolve("user").resolve(".chatmap");
        Files.createDirectories(current.resolve(".chatmap-local"));
        Files.createDirectories(legacy);

        ChatMapPaths.ParsedArguments parsed = ChatMapPaths.resolve(
                List.of("--home", explicit.toString(), "archive.zip"),
                Map.of(ChatMapPaths.CHATMAP_HOME, envHome.toString()),
                tempDir.resolve("user"),
                current);

        assertEquals(explicit.toAbsolutePath().normalize(), parsed.paths().homeDirectory());
        assertEquals(explicit.toAbsolutePath().normalize().resolve("transcripts"), parsed.paths().transcriptsDirectory());
        assertEquals(List.of("archive.zip"), parsed.remainingArgs());
    }

    @Test
    void nonblankChatMapHomeWinsOverCurrentAndLegacyHomes() throws Exception {
        Path envHome = tempDir.resolve("env-home");
        Path current = tempDir.resolve("repo");
        Path legacy = tempDir.resolve("user").resolve(".chatmap");
        Files.createDirectories(current.resolve(".chatmap-local"));
        Files.createDirectories(legacy);

        ChatMapPaths.ParsedArguments parsed = ChatMapPaths.resolve(
                List.of(),
                Map.of(ChatMapPaths.CHATMAP_HOME, envHome.toString()),
                tempDir.resolve("user"),
                current);

        assertEquals(envHome.toAbsolutePath().normalize(), parsed.paths().homeDirectory());
    }

    @Test
    void blankChatMapHomeIsIgnored() throws Exception {
        Path current = tempDir.resolve("repo");
        Path currentHome = current.resolve(".chatmap-local");
        Files.createDirectories(currentHome);

        ChatMapPaths.ParsedArguments parsed = ChatMapPaths.resolve(
                List.of(),
                Map.of(ChatMapPaths.CHATMAP_HOME, "  "),
                tempDir.resolve("user"),
                current);

        assertEquals(currentHome.toAbsolutePath().normalize(), parsed.paths().homeDirectory());
    }

    @Test
    void existingCurrentDirectoryHomeWinsOverLegacyHome() throws Exception {
        Path current = tempDir.resolve("repo");
        Path currentHome = current.resolve(".chatmap-local");
        Path legacy = tempDir.resolve("user").resolve(".chatmap");
        Files.createDirectories(currentHome);
        Files.createDirectories(legacy);

        ChatMapPaths.ParsedArguments parsed = ChatMapPaths.resolve(List.of(), Map.of(), tempDir.resolve("user"), current);

        assertEquals(currentHome.toAbsolutePath().normalize(), parsed.paths().homeDirectory());
    }

    @Test
    void missingCurrentDirectoryHomeFallsBackToExistingLegacyHome() throws Exception {
        Path legacy = tempDir.resolve("user").resolve(".chatmap");
        Files.createDirectories(legacy);
        Files.createFile(legacy.resolve("chatmap.db"));

        ChatMapPaths.ParsedArguments parsed = ChatMapPaths.resolve(
                List.of(),
                Map.of(),
                tempDir.resolve("user"),
                tempDir.resolve("repo"));

        assertEquals(legacy.toAbsolutePath().normalize(), parsed.paths().homeDirectory());
    }

    @Test
    void legacyDirectoryWithoutDatabaseIsIgnored() throws Exception {
        Path current = tempDir.resolve("repo");
        Path legacy = tempDir.resolve("user").resolve(".chatmap");
        Files.createDirectories(legacy.resolve("logs"));

        assertThrows(IllegalArgumentException.class,
                () -> ChatMapPaths.resolve(List.of(), Map.of(), tempDir.resolve("user"), current));

        assertFalse(Files.exists(legacy.resolve("chatmap.db")));
    }

    @Test
    void noCandidateFailsClearlyAndCreatesNothing() {
        Path current = tempDir.resolve("repo");
        Path legacy = tempDir.resolve("user").resolve(".chatmap");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ChatMapPaths.resolve(List.of(), Map.of(), tempDir.resolve("user"), current));

        assertTrue(error.getMessage().contains("--home"));
        assertTrue(error.getMessage().contains(ChatMapPaths.CHATMAP_HOME));
        assertTrue(error.getMessage().contains(current.resolve(".chatmap-local").toAbsolutePath().normalize().toString()));
        assertTrue(error.getMessage().contains(legacy.toAbsolutePath().normalize().toString()));
        assertFalse(Files.exists(current.resolve(".chatmap-local")));
        assertFalse(Files.exists(legacy));
    }

    @Test
    void resultsAreNormalizedAndDatabaseDefaultsInsideSelectedHome() {
        Path selected = tempDir.resolve("a").resolve("..").resolve("home");

        ChatMapPaths.ParsedArguments parsed = ChatMapPaths.resolve(
                List.of("--home", selected.toString()),
                Map.of(),
                tempDir.resolve("user"),
                tempDir.resolve("repo"));

        Path expectedHome = selected.toAbsolutePath().normalize();
        assertEquals(expectedHome, parsed.paths().homeDirectory());
        assertEquals(expectedHome.resolve("chatmap.db"), parsed.paths().databasePath());
        assertEquals(expectedHome.resolve("logs"), parsed.paths().logsDirectory());
    }

    @Test
    void pathsContainingSpacesArePreserved() {
        Path selected = tempDir.resolve("home with spaces");

        ChatMapPaths.ParsedArguments parsed = ChatMapPaths.resolve(
                List.of("--home", selected.toString(), "file with spaces.zip"),
                Map.of(),
                tempDir.resolve("user"),
                tempDir.resolve("repo"));

        assertEquals(selected.toAbsolutePath().normalize(), parsed.paths().homeDirectory());
        assertEquals(List.of("file with spaces.zip"), parsed.remainingArgs());
    }

    @Test
    void explicitResolutionDoesNotCreateDirectories() {
        Path selected = tempDir.resolve("not-created");

        ChatMapPaths.resolve(List.of("--home", selected.toString()), Map.of(), tempDir.resolve("user"), tempDir);

        assertFalse(Files.exists(selected));
    }

    @Test
    void missingHomeValueIsAUsageError() {
        assertThrows(IllegalArgumentException.class,
                () -> ChatMapPaths.resolve(List.of("--home"), Map.of(), tempDir.resolve("user"), tempDir));
    }

    @Test
    void unrecognizedOptionsPassThroughToRemainingArgsForTheCallingCliToValidate() throws Exception {
        Path current = tempDir.resolve("repo");
        Files.createDirectories(current.resolve(".chatmap-local"));

        ChatMapPaths.ParsedArguments parsed = ChatMapPaths.resolve(
                List.of("--source", "Gemini (web)"), Map.of(), tempDir.resolve("user"), current);

        assertEquals(List.of("--source", "Gemini (web)"), parsed.remainingArgs());
    }

    @Test
    void gitBashStyleWindowsPathIsAcceptedOnWindows() {
        ChatMapPaths.ParsedArguments parsed = ChatMapPaths.resolve(
                List.of("--home", "/c/Users/ray/chatmap-data"),
                Map.of(),
                tempDir.resolve("user"),
                tempDir);

        if (java.io.File.separatorChar == '\\') {
            assertEquals(Path.of("C:\\Users\\ray\\chatmap-data").toAbsolutePath().normalize(),
                    parsed.paths().homeDirectory());
        } else {
            assertEquals(Path.of("/c/Users/ray/chatmap-data").toAbsolutePath().normalize(),
                    parsed.paths().homeDirectory());
        }
    }
}
