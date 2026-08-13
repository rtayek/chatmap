package chatmap.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.app.bootstrap.LoggingBootstrap;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;

final class ImportChatGptArchiveCliTest {

    @TempDir
    Path tempDir;

    private PrintStream originalOutput;
    private String originalLogDirectory;

    @BeforeEach
    void rememberProcessState() {
        originalOutput = System.out;
        originalLogDirectory = System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);
    }

    @AfterEach
    void restoreProcessState() {
        System.setOut(originalOutput);
        LoggingBootstrap.initializeTemporaryFallback();
        restoreProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY, originalLogDirectory);
    }

    @Test
    void importsArchiveIntoExplicitHomeAndReportsResult() throws Exception {
        Path home = tempDir.resolve("home");
        Path archive = archive();
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputBytes, true, StandardCharsets.UTF_8));

        ImportChatGptArchiveCli.run(CliBootstrap.parse(new String[] {
                "--home", home.toString(), archive.toString()
        }));

        String output = outputBytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("conversation entries: 1"));
        assertTrue(output.contains("codex.json: missing"));
        try (Connection connection = new Database(
                "jdbc:sqlite:" + home.resolve("chatmap.db")).openAndInitialize()) {
            var chats = new ChatRepository(connection).findAll();
            assertEquals(1, chats.size());
            assertEquals("CLI archive", chats.getFirst().title());
        }
    }

    private Path archive() throws Exception {
        Path archive = tempDir.resolve("chatgpt-export.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("conversations.json"));
            zip.write(conversationJson().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return archive;
    }

    private static String conversationJson() {
        return """
                [{
                  "conversation_id":"cli-archive",
                  "title":"CLI archive",
                  "create_time":1000.0,
                  "update_time":1001.0,
                  "current_node":"m1",
                  "mapping":{
                    "root":{"id":"root","parent":null,"message":null},
                    "m1":{"id":"m1","parent":"root","message":{
                      "id":"m1",
                      "author":{"role":"user"},
                      "create_time":1000.0,
                      "content":{"content_type":"text","parts":["hello"]}
                    }}
                  }
                }]
                """;
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
