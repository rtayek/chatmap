package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceScanServiceTest {

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
        Map<String, List<Path>> found = WorkspaceScanService.scanWorkspace(relativeRoot);

        assertTrue(found.containsKey("proj"), "project under a relative parent root should be found");
        List<Path> files = found.get("proj");
        assertEquals(1, files.size(), "only the real chat file, not the one under .git");
        assertEquals("dev-chat.md", files.get(0).getFileName().toString());
    }

    @Test
    void candidateMatchingExcludesScriptsBinariesAndConsolidatedOutput() {
        // Real transcripts still match.
        assertTrue(WorkspaceScanService.isCandidateChatFile(Path.of("chatmap-development-session.md")));
        assertTrue(WorkspaceScanService.isCandidateChatFile(Path.of("HANDOFF.md")));
        assertTrue(WorkspaceScanService.isCandidateChatFile(Path.of("transcript.txt")));

        // Keyword-in-name false positives are rejected.
        assertFalse(WorkspaceScanService.isCandidateChatFile(Path.of("chatgpt-web-sessions.sh")));
        assertFalse(WorkspaceScanService.isCandidateChatFile(Path.of("sessions.sh")));
        assertFalse(WorkspaceScanService.isCandidateChatFile(Path.of("wechat_qr.png")));
        // Our own output must not be re-ingested.
        assertFalse(WorkspaceScanService.isCandidateChatFile(Path.of("existing-chat_CONSOLIDATED.md")));
    }

    @Test
    void ignoresHiddenAndBuildDirsButNotDotDotSegment() {
        assertTrue(WorkspaceScanService.isIgnoredDirName(".git"));
        assertTrue(WorkspaceScanService.isIgnoredDirName("node_modules"));
        assertTrue(WorkspaceScanService.isIgnoredDirName("build"));
        assertFalse(WorkspaceScanService.isIgnoredDirName(".."), "'..' is traversal, not a hidden dir");
        assertFalse(WorkspaceScanService.isIgnoredDirName("."), "'.' is traversal, not a hidden dir");
        assertFalse(WorkspaceScanService.isIgnoredDirName("myclaw"));
    }
}
