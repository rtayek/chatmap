package chatmap.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ChatMapRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    void opensExplicitHomeInitializesDatabaseAndController() throws Exception {
        Path home = tempDir.resolve("runtime-home");

        try (ChatMapRuntime runtime = ChatMapRuntime.open(List.of("--home", home.toString()))) {
            assertEquals(home.toAbsolutePath().normalize(), runtime.paths().homeDirectory());
            assertTrue(Files.isDirectory(home));
            assertTrue(Files.isRegularFile(home.resolve("chatmap.db")));
            assertEquals("Loaded 0 chats.", runtime.controller().loadAllChats().statusText());
        }
    }

    @Test
    void closeClosesTheDatabaseConnection() throws Exception {
        Path home = tempDir.resolve("close-home");
        ChatMapRuntime runtime = ChatMapRuntime.open(List.of("--home", home.toString()));
        runtime.close();

        SQLException closed = assertThrows(SQLException.class,
                () -> runtime.controller().loadAllChats());
        assertTrue(closed.getMessage().toLowerCase(java.util.Locale.ROOT).contains("closed"));
    }

    @Test
    void rejectsUnexpectedArgumentsWithoutCreatingHome() {
        Path home = tempDir.resolve("bad-home");

        assertThrows(IllegalArgumentException.class,
                () -> ChatMapRuntime.open(List.of("--home", home.toString(), "extra")));

        assertFalse(Files.exists(home));
    }
}
