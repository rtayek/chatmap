package chatmap.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.config.LoggingBootstrap;
import chatmap.storage.Database;
import chatmap.storage.ProjectRepository;

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
        Path projectDir = tempDir.resolve("sampleProject");
        Files.createDirectories(projectDir);

        Path chatFile = projectDir.resolve("sample-chat.md");
        Files.writeString(chatFile, """
                # Sample Project Discussion
                
                User: How do we structure the consolidation module?
                
                Assistant: We build a clean Java CLI tool within ChatMap that scans workspace folders.
                """);

        Path outputDir = tempDir.resolve("output");

        // Run CLI on temporary workspace
        ChatConsolidatorCli.main(new String[]{tempDir.toString(), outputDir.toString()});

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

    @Test
    void scanFindsChatsThroughRelativeParentPath(@TempDir Path tempDir) throws Exception {
        // Regression: a root reached via a ".." segment used to match nothing,
        // because every walked path contained "..", and "..".startsWith(".").
        Path workspace = tempDir.resolve("workspace");
        Path project = workspace.resolve("proj");
        Files.createDirectories(project);
        Files.writeString(project.resolve("dev-chat.md"), "# Dev\n\nUser: hi\n");

        // A hidden dir with a chat-named file that must be pruned, not imported.
        Path hidden = project.resolve(".git");
        Files.createDirectories(hidden);
        Files.writeString(hidden.resolve("chat.md"), "# should be ignored");

        Path relativeRoot = workspace.resolve("..").resolve("workspace"); // contains ".."
        Map<String, List<Path>> found = ChatConsolidatorCli.scanWorkspace(relativeRoot);

        assertTrue(found.containsKey("proj"), "project under a relative parent root should be found");
        List<Path> files = found.get("proj");
        assertEquals(1, files.size(), "only the real chat file, not the one under .git");
        assertEquals("dev-chat.md", files.get(0).getFileName().toString());
    }

    @Test
    void candidateMatchingExcludesScriptsBinariesAndConsolidatedOutput() {
        // Real transcripts still match.
        assertTrue(ChatConsolidatorCli.isCandidateChatFile(Path.of("chatmap-development-session.md")));
        assertTrue(ChatConsolidatorCli.isCandidateChatFile(Path.of("HANDOFF.md")));
        assertTrue(ChatConsolidatorCli.isCandidateChatFile(Path.of("transcript.txt")));

        // Keyword-in-name false positives are rejected.
        assertFalse(ChatConsolidatorCli.isCandidateChatFile(Path.of("chatgpt-web-sessions.sh")));
        assertFalse(ChatConsolidatorCli.isCandidateChatFile(Path.of("sessions.sh")));
        assertFalse(ChatConsolidatorCli.isCandidateChatFile(Path.of("wechat_qr.png")));
        // Our own output must not be re-ingested.
        assertFalse(ChatConsolidatorCli.isCandidateChatFile(Path.of("existing-chat_CONSOLIDATED.md")));
    }

    @Test
    void ignoresHiddenAndBuildDirsButNotDotDotSegment() {
        assertTrue(ChatConsolidatorCli.isIgnoredDirName(".git"));
        assertTrue(ChatConsolidatorCli.isIgnoredDirName("node_modules"));
        assertTrue(ChatConsolidatorCli.isIgnoredDirName("build"));
        assertFalse(ChatConsolidatorCli.isIgnoredDirName(".."), "'..' is traversal, not a hidden dir");
        assertFalse(ChatConsolidatorCli.isIgnoredDirName("."), "'.' is traversal, not a hidden dir");
        assertFalse(ChatConsolidatorCli.isIgnoredDirName("myclaw"));
    }
}
