package chatmap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

final class LoggingBootstrapTest {

    @TempDir
    Path tempDir;

    private String originalLogDirectory;
    private String originalUserHome;

    @BeforeEach
    void rememberSystemProperties() {
        originalLogDirectory = System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);
        originalUserHome = System.getProperty("user.home");
    }

    @AfterEach
    void restoreSystemProperties() {
        LoggingBootstrap.initializeTemporaryFallback();
        restoreProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY, originalLogDirectory);
        restoreProperty("user.home", originalUserHome);
    }

    @Test
    void explicitHomeConfiguresItsLogsDirectory() {
        Path home = tempDir.resolve("explicit-home");

        ChatMapPaths.ParsedArguments parsed = LoggingBootstrap.bootstrap(
                new String[] { "--home", home.toString() });

        assertEquals(home.toAbsolutePath().normalize().resolve("logs"), parsed.paths().logsDirectory());
        assertEquals(parsed.paths().logsDirectory().toString(),
                System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY));
        assertTrue(Files.isDirectory(parsed.paths().logsDirectory()));
        assertFalse(Files.exists(parsed.paths().databasePath()));
    }

    @Test
    void configuredAndProjectLocalHomesUseTheirOwnLogsDirectories() throws Exception {
        Path configuredHome = tempDir.resolve("configured-home");
        ChatMapPaths.ParsedArguments configured = ChatMapPaths.resolve(
                List.of(), Map.of(ChatMapPaths.CHATMAP_HOME, configuredHome.toString()),
                tempDir.resolve("user"), tempDir.resolve("repo"));

        LoggingBootstrap.initialize(configured.paths());

        assertEquals(configuredHome.toAbsolutePath().normalize().resolve("logs").toString(),
                System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY));
        assertTrue(Files.isDirectory(configured.paths().logsDirectory()));

        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve(".chatmap-local"));
        ChatMapPaths.ParsedArguments projectLocal = ChatMapPaths.resolve(
                List.of(), Map.of(), tempDir.resolve("user"), project);

        LoggingBootstrap.initialize(projectLocal.paths());

        assertEquals(project.resolve(".chatmap-local").toAbsolutePath().normalize().resolve("logs").toString(),
                System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY));
        assertTrue(Files.isDirectory(projectLocal.paths().logsDirectory()));
    }

    @Test
    void earlyParsingFailureRetainsTemporaryFallbackAndCreatesNoLegacyHome() throws Exception {
        Path userHome = tempDir.resolve("user");
        System.setProperty("user.home", userHome.toString());

        assertThrows(IllegalArgumentException.class,
                () -> LoggingBootstrap.bootstrap(new String[] { "--home" }));

        assertEquals(LoggingBootstrap.temporaryLogDirectory().toString(),
                System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY));
        Path fallbackLog = LoggingBootstrap.temporaryLogDirectory().resolve("chatmap.log");
        assertTrue(Files.isRegularFile(fallbackLog));
        assertTrue(Files.readString(fallbackLog).contains("ChatMap startup failed before application initialization."));
        assertFalse(Files.exists(userHome.resolve(".chatmap")));
    }

    @Test
    void pathResolutionIsIndependentOfLoggingAndBootstrapHasNoStaticLogger() {
        System.clearProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);

        ChatMapPaths.resolve(List.of("--home", tempDir.resolve("home").toString()),
                Map.of(), tempDir.resolve("user"), tempDir.resolve("repo"));

        assertNull(System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY));
        boolean hasStaticLogger = List.of(LoggingBootstrap.class.getDeclaredFields()).stream()
                .anyMatch(field -> Modifier.isStatic(field.getModifiers())
                        && Logger.class.isAssignableFrom(field.getType()));
        assertFalse(hasStaticLogger);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
