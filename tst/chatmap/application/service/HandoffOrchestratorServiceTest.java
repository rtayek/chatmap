package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;

class HandoffOrchestratorServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path inbox;

    private Path projectDir(String name) throws IOException {
        Path dir = inbox.resolve(name);
        Files.createDirectories(dir);
        return dir;
    }

    private Path writeTask(Path dir, String fileName, String agent, String branch, String body) throws IOException {
        Path file = dir.resolve(fileName);
        Files.writeString(file, "---\nagent: " + agent + "\nbranch: " + branch + "\n---\n" + body + "\n");
        return file;
    }

    @Test
    void scanFindsMdFilesButIgnoresTemplateArchiveAndHiddenDirs() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        Path other = projectDir("otherproj");
        Files.createDirectories(inbox.resolve(".git"));
        Files.writeString(inbox.resolve(".git/config"), "not a task");
        Path a = writeTask(chatmapDir, "a.md", "claude", "b1", "body");
        writeTask(chatmapDir, "Template.md", "claude", "b1", "body");
        Path archiveDir = chatmapDir.resolve("archive");
        Files.createDirectories(archiveDir);
        Files.writeString(archiveDir.resolve("old.md"), "---\nagent: claude\nbranch: b\n---\nold\n");
        Path b = writeTask(other, "b.md", "codex", "b2", "body");

        List<Path> found = HandoffOrchestratorService.scanForTaskFiles(inbox);

        assertEquals(List.of(a, b).stream()
                .sorted(java.util.Comparator.comparing(Path::toString)).toList(), found);
    }

    @Test
    void successfulTaskWithChangesArchivesFileAndCommitsWorktreeChanges() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        Path task = writeTask(chatmapDir, "task1.md", "claude", "feature-x", "do the thing");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(" M file.txt\n"));
        HandoffOrchestratorService service = new HandoffOrchestratorService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), CLOCK, false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertEquals(1, results.size());
        HandoffRunResult result = results.get(0);
        assertEquals(HandoffRunResult.Outcome.success, result.outcome());
        assertEquals("chatmap", result.projectKey());
        assertTrue(result.pushPending());
        assertTrue(result.detail().contains("committed changes"), result.detail());

        assertFalse(Files.exists(task), "original task file should have been archived away");
        assertTrue(Files.exists(chatmapDir.resolve("archive").resolve("task1.md")));

        assertTrue(executor.calledWithPrefix("git worktree add"));
        assertTrue(executor.calledWithPrefix("git commit -m Handoff: feature-x"));
        assertFalse(executor.calledWithPrefix("git push"), "autoPush=false must never push");
        assertTrue(executor.calledWithPrefix("git worktree remove --force"));
    }

    @Test
    void successfulTaskWithNoChangesArchivesButDoesNotCommitWorktree() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        writeTask(chatmapDir, "task1.md", "claude", "feature-x", "do the thing");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(""));
        HandoffOrchestratorService service = new HandoffOrchestratorService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), CLOCK, false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        HandoffRunResult result = results.get(0);
        assertEquals(HandoffRunResult.Outcome.success, result.outcome());
        assertTrue(result.detail().contains("no changes"), result.detail());
        assertFalse(executor.calledWithPrefix("git commit -m Handoff:"),
                "no worktree changes means no worktree commit, even though the inbox archive commit still happens");
        assertTrue(executor.calledWithPrefix("git commit -m Archive completed handoff"));
        assertTrue(Files.exists(chatmapDir.resolve("archive").resolve("task1.md")));
    }

    @Test
    void autoPushTrueActuallyPushesBothRepos() throws IOException {
        projectDir("chatmap");
        Path chatmapDir = inbox.resolve("chatmap");
        writeTask(chatmapDir, "task1.md", "claude", "feature-x", "do the thing");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(" M file.txt\n"));
        HandoffOrchestratorService service = new HandoffOrchestratorService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), CLOCK, true);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertFalse(results.get(0).pushPending());
        assertTrue(executor.calledWithPrefix("git push -u origin feature-x"));
        assertTrue(executor.calls().stream().anyMatch(c -> c.command().equals(List.of("git", "push"))));
    }

    @Test
    void agentFailureWritesFailureReportAndLeavesOriginalFileInPlace() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        Path task = writeTask(chatmapDir, "task1.md", "claude", "feature-x", "do the thing");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("claude -p", new CommandResult(1, "", "boom", Duration.ofSeconds(1), false));
        HandoffOrchestratorService service = new HandoffOrchestratorService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), CLOCK, false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        HandoffRunResult result = results.get(0);
        assertEquals(HandoffRunResult.Outcome.failure, result.outcome());
        assertTrue(result.detail().contains("exited with status 1"), result.detail());
        assertTrue(result.detail().contains("boom"), result.detail());
        assertTrue(Files.exists(task), "a failed task's source file must not be moved");
        assertTrue(executor.calledWithPrefix("git worktree remove --force"), "cleanup must still run on failure");

        try (var files = Files.list(chatmapDir)) {
            assertTrue(files.anyMatch(p -> {
                Path name = p.getFileName();
                return name != null && name.toString().startsWith("failure-report-");
            }));
        }
    }

    @Test
    void worktreeAddFailureIsReportedWithoutRunningTheAgent() throws IOException {
        projectDir("chatmap");
        Path chatmapDir = inbox.resolve("chatmap");
        writeTask(chatmapDir, "task1.md", "claude", "feature-x", "do the thing");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git worktree add", new CommandResult(1, "", "not a repo", Duration.ofMillis(10), false));
        HandoffOrchestratorService service = new HandoffOrchestratorService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), CLOCK, false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        HandoffRunResult result = results.get(0);
        assertEquals(HandoffRunResult.Outcome.failure, result.outcome());
        assertTrue(result.detail().contains("git worktree add failed"), result.detail());
        assertFalse(executor.calledWithPrefix("claude -p"), "the agent must never run if the worktree could not be created");
    }

    @Test
    void missingProjectRegistryEntryFailsFastWithoutTouchingGit() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        writeTask(chatmapDir, "task1.md", "claude", "feature-x", "do the thing");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        HandoffOrchestratorService service = new HandoffOrchestratorService(
                executor, Map.of(), CLOCK, false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        HandoffRunResult result = results.get(0);
        assertEquals(HandoffRunResult.Outcome.failure, result.outcome());
        assertTrue(result.detail().contains("No configured target project path"), result.detail());
        assertFalse(executor.calledWithPrefix("git worktree"));
    }

    @Test
    void malformedFrontmatterIsReportedAsFailure() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        Files.writeString(chatmapDir.resolve("bad.md"), "---\nagent: claude\n---\nno branch field\n");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        HandoffOrchestratorService service = new HandoffOrchestratorService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), CLOCK, false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        HandoffRunResult result = results.get(0);
        assertEquals(HandoffRunResult.Outcome.failure, result.outcome());
        assertTrue(result.detail().contains("Could not parse handoff file"), result.detail());
    }

    @Test
    void templateFileIsNeverProcessed() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        writeTask(chatmapDir, "template.md", "claude", "x", "should never run");
        writeTask(chatmapDir, "real-task.md", "claude", "feature-y", "do it");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        HandoffOrchestratorService service = new HandoffOrchestratorService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), CLOCK, false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertEquals(1, results.size());
        assertEquals("real-task.md", results.get(0).sourceFile().getFileName().toString());
    }

    private static CommandResult ok() {
        return ok("");
    }

    private static CommandResult ok(String stdout) {
        return new CommandResult(0, stdout, "", Duration.ofMillis(10), false);
    }

    /** Records every request and returns a scripted result for the first matching command-prefix key. */
    private static final class FakeCommandExecutor implements CommandExecutor {
        private final List<CommandRequest> calls = new ArrayList<>();
        private final Map<String, CommandResult> responses = new LinkedHashMap<>();

        void respond(String commandPrefix, CommandResult result) {
            responses.put(commandPrefix, result);
        }

        List<CommandRequest> calls() {
            return calls;
        }

        boolean calledWithPrefix(String prefix) {
            return calls.stream().anyMatch(c -> String.join(" ", c.command()).startsWith(prefix));
        }

        @Override
        public CommandResult run(CommandRequest request) {
            calls.add(request);
            String key = String.join(" ", request.command());
            for (Map.Entry<String, CommandResult> entry : responses.entrySet()) {
                if (key.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return ok();
        }
    }
}
