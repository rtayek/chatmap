package chatmap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ChatMapPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void nonblankChatMapHomeOverridesDefault() {
        Path override = tempDir.resolve("custom-home");

        Path resolved = ChatMapPaths.resolve(Map.of(ChatMapPaths.CHATMAP_HOME, override.toString()), tempDir);

        assertEquals(override.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void blankChatMapHomeFallsBackToUserHome() {
        Path resolved = ChatMapPaths.resolve(Map.of(ChatMapPaths.CHATMAP_HOME, "  "), tempDir);

        assertEquals(tempDir.resolve(".chatmap").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void absentChatMapHomeFallsBackToUserHome() {
        Path resolved = ChatMapPaths.resolve(Map.of(), tempDir);

        assertEquals(tempDir.resolve(".chatmap").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void relativeOverrideIsNormalizedToAbsolutePath() {
        Path resolved = ChatMapPaths.resolve(Map.of(ChatMapPaths.CHATMAP_HOME, "relative/../chatmap-data"), tempDir);

        assertTrue(resolved.isAbsolute());
        assertEquals(Path.of("chatmap-data").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void pureResolutionDoesNotCreateDirectories() {
        Path override = tempDir.resolve("not-created");

        ChatMapPaths.resolve(Map.of(ChatMapPaths.CHATMAP_HOME, override.toString()), tempDir);

        assertFalse(Files.exists(override));
    }

    @Test
    void gitBashStyleWindowsPathIsAcceptedOnWindows() {
        Path resolved = ChatMapPaths.resolve(Map.of(ChatMapPaths.CHATMAP_HOME, "/c/Users/ray/chatmap-data"), tempDir);

        if (java.io.File.separatorChar == '\\') {
            assertEquals(Path.of("C:\\Users\\ray\\chatmap-data").toAbsolutePath().normalize(), resolved);
        } else {
            assertEquals(Path.of("/c/Users/ray/chatmap-data").toAbsolutePath().normalize(), resolved);
        }
    }
}
