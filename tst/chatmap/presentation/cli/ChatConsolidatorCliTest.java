package chatmap.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.app.bootstrap.LoggingBootstrap;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;

class ChatConsolidatorCliTest {

    private String originalLogDirectory;

    @BeforeEach
    void rememberLogDirectoryProperty() {
        originalLogDirectory = System.getProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);
    }

    @AfterEach
    void releaseLogFileAndRestoreProperty() {
        LoggingBootstrap.initializeTemporaryFallback();
        restoreLogDirectoryProperty(originalLogDirectory);
    }

    @Test
    void consolidatorProcessesProjectFiles(@TempDir Path tempDir) throws Exception {
        Path home = tempDir.resolve("home");
        Path workspace = tempDir.resolve("workspace");
        Path projectDir = workspace.resolve("sampleProject");
        Files.createDirectories(projectDir);

        Path chatFile = projectDir.resolve("sample-chat.md");
        Files.writeString(chatFile, """
                # Sample Project Discussion
                
                User: How do we structure the consolidation module?
                
                Assistant: We build a clean Java CLI tool within ChatMap that scans workspace folders.
                """);

        Path outputDir = tempDir.resolve("output");

        // Run CLI on temporary workspace
        ChatConsolidatorCli.main(new String[]{
                "--home", home.toString(), workspace.toString(), outputDir.toString()
        });

        Path consolidatedFile = outputDir.resolve("sampleProject_CONSOLIDATED.md");
        assertTrue(Files.exists(consolidatedFile), "Consolidated Markdown file should be generated");

        String content = Files.readString(consolidatedFile);
        assertTrue(content.contains("sampleProject"));
        assertTrue(content.contains("Sample Project Discussion"));
        assertTrue(content.contains("How do we structure the consolidation module?"));
    }

    private static void restoreLogDirectoryProperty(String value) {
        if (value == null) {
            System.clearProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY);
        } else {
            System.setProperty(LoggingBootstrap.LOG_DIRECTORY_PROPERTY, value);
        }
    }

    @Test
    void rerunningTheConsolidatorNeverCreatesDuplicateProjects(@TempDir Path tempDir) throws Exception {
        Path home = tempDir.resolve("home");
        Path workspace = tempDir.resolve("workspace");
        Path projectDir = workspace.resolve("repeatProject");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("chat.md"), "# Repeat\n\nUser: hi\n");

        Path outputDir = tempDir.resolve("output");
        String[] cliArgs = {"--home", home.toString(), workspace.toString(), outputDir.toString()};

        ChatConsolidatorCli.main(cliArgs);
        ChatConsolidatorCli.main(cliArgs);

        try (Connection conn = new Database("jdbc:sqlite:" + home.resolve("chatmap.db")).openAndInitialize()) {
            ProjectRepository projects = new ProjectRepository(conn);
            long matching = projects.findAll().stream()
                    .filter(p -> p.name().equals("repeatProject"))
                    .count();
            assertEquals(1, matching, "rerunning the consolidator must not duplicate the project");
        }
    }

}
