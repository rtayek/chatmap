package chatmap.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.app.bootstrap.LoggingBootstrap;
import chatmap.infrastructure.importer.GeminiTakeoutFixture;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.MessageRepository;

class ImportGeminiTakeoutCliTest {
    @BeforeEach
    void rememberProcessState() {
        originalOutput = System.out;
        originalLogDirectory = System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);
    }

    @AfterEach
    void restoreProcessState() {
        System.setOut(originalOutput);
        LoggingBootstrap.initializeTemporaryFallback();
        if (originalLogDirectory == null) {
            System.clearProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);
        } else {
            System.setProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY, originalLogDirectory);
        }
    }

    @Test
    void importsIntoExplicitHomeAndReportsRepeatImport() throws Exception {
        Path home = tempDir.resolve("isolated home");
        Path takeout = tempDir.resolve("Takeout");
        GeminiTakeoutFixture.write(takeout, "1786837272");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            for (int i = 0; i < 2; i++) {
                ImportGeminiTakeoutCli.run(CliBootstrap.parse(new String[] {
                        "--home", home.toString(), takeout.toString()
                }));
            }
        }
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("inserted 1, updated 0, unchanged 0"));
        assertTrue(output.contains("inserted 0, updated 0, unchanged 1"));
        try (Connection connection = new Database("jdbc:sqlite:" + home.resolve("chatmap.db")).openAndInitialize()) {
            var chats = new ChatRepository(connection).findAll();
            assertEquals(1, chats.size());
            assertEquals("Sanitized Gemini sample", chats.getFirst().title());
            assertEquals(16, new MessageRepository(connection).findByChat(chats.getFirst().id()).size());
        }
    }

    @TempDir
    Path tempDir;
    private PrintStream originalOutput;
    private String originalLogDirectory;
}
