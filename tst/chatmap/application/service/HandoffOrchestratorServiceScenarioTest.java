package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.Channel;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;
import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.infrastructure.llm.ClaudeCliProvider;
import chatmap.infrastructure.llm.CodexCliProvider;
import chatmap.infrastructure.handoff.FileSystemHandoffFileStore;

/**
 * Scenario coverage for {@link HandoffOrchestratorService} that complements
 * {@code HandoffOrchestratorServiceTest}. Where that suite covers each git
 * lifecycle step in isolation, this one covers multi-task runs, per-task
 * isolation, agent-routing edge cases, and eligibility boundaries that the
 * single-task tests don't reach. Same hermetic harness: {@code @TempDir}
 * inbox, a scripted {@link FakeCommandExecutor}, and a real
 * {@link FileSystemHandoffFileStore} -- no network, no git, no real agent.
 */
class HandoffOrchestratorServiceScenarioTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC);
    private static final HandoffFileStore FILE_STORE = new FileSystemHandoffFileStore();

    @TempDir
    Path inbox;

    private static HandoffOrchestratorService newService(
            FakeCommandExecutor executor, Map<String, Path> registry, boolean autoPush) {
        EnumMap<Channel, LlmProvider> providers = new EnumMap<>(Channel.class);
        providers.put(Channel.claudeCli, new ClaudeCliProvider(executor, Duration.ofMinutes(30)));
        providers.put(Channel.codexCli, new CodexCliProvider(executor, Duration.ofMinutes(30)));
        return new HandoffOrchestratorService(executor, providers,
                Map.of("claude", ModelTarget.claude, "codex", ModelTarget.codex),
                FILE_STORE, registry, CLOCK, autoPush);
    }

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

    // ---- multi-task runs --------------------------------------------------

    @Test
    void processesEveryEligibleTaskInASingleRun() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        writeTask(chatmapDir, "task-a.md", "claude", "feature-a", "do a");
        writeTask(chatmapDir, "task-b.md", "claude", "feature-b", "do b");
        writeTask(chatmapDir, "task-c.md", "claude", "feature-c", "do c");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(""));
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertEquals(3, results.size(), "all three eligible tasks should be processed in one pass");
        assertTrue(results.stream().allMatch(r -> r.outcome() == HandoffRunResult.Outcome.success));
    }

    @Test
    void tasksAreProcessedInDeterministicSortedOrder() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        // Written out of order on purpose.
        writeTask(chatmapDir, "zebra.md", "claude", "b-z", "z");
        writeTask(chatmapDir, "alpha.md", "claude", "b-a", "a");
        writeTask(chatmapDir, "mango.md", "claude", "b-m", "m");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(""));
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        List<String> order = results.stream()
                .map(r -> java.util.Objects.requireNonNull(r.sourceFile().getFileName()).toString())
                .toList();
        assertEquals(List.of("alpha.md", "mango.md", "zebra.md"), order,
                "processing order must be sorted by path, not filesystem iteration order");
    }

    @Test
    void oneTaskFailingDoesNotStopLaterTasksInTheSameRun() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        // 'aaa' sorts first and fails at the agent; 'zzz' sorts last and must still run.
        writeTask(chatmapDir, "aaa-fails.md", "claude", "feature-fail", "boom");
        writeTask(chatmapDir, "zzz-ok.md", "claude", "feature-ok", "fine");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(""));
        // Only the first task's agent invocation fails; the fake matches by prefix,
        // so distinguish via body isn't possible -- instead fail the first worktree add.
        executor.respondOnce("git worktree add",
                new CommandResult(1, "", "locked", Duration.ofMillis(10), false));
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertEquals(2, results.size());
        assertEquals(HandoffRunResult.Outcome.failure, results.get(0).outcome(),
                "the first task (sorted) hit the one-shot worktree-add failure");
        assertEquals(HandoffRunResult.Outcome.success, results.get(1).outcome(),
                "a failure in one task must not abort the rest of the run");
    }

    @Test
    void tasksAcrossMultipleProjectFoldersAllRun() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        Path myclawDir = projectDir("myclaw");
        writeTask(chatmapDir, "c.md", "claude", "feature-c", "c");
        writeTask(myclawDir, "m.md", "claude", "feature-m", "m");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(""));
        HandoffOrchestratorService service = newService(
                executor, Map.of(
                        "chatmap", Path.of("chatmap-repo"),
                        "myclaw", Path.of("myclaw-repo")),
                false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> r.projectKey().equals("chatmap")));
        assertTrue(results.stream().anyMatch(r -> r.projectKey().equals("myclaw")));
    }

    @Test
    void unregisteredProjectFailsButRegisteredSiblingStillRuns() throws IOException {
        Path known = projectDir("chatmap");
        Path unknown = projectDir("mysteryproj");
        writeTask(known, "ok.md", "claude", "feature-ok", "fine");
        writeTask(unknown, "orphan.md", "claude", "feature-x", "no home");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(""));
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("chatmap-repo")), false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        HandoffRunResult orphan = results.stream()
                .filter(r -> r.projectKey().equals("mysteryproj")).findFirst().orElseThrow();
        HandoffRunResult ok = results.stream()
                .filter(r -> r.projectKey().equals("chatmap")).findFirst().orElseThrow();
        assertEquals(HandoffRunResult.Outcome.failure, orphan.outcome());
        assertTrue(orphan.detail().contains("No configured target project path"), orphan.detail());
        assertEquals(HandoffRunResult.Outcome.success, ok.outcome());
    }

    // ---- agent routing edge cases -----------------------------------------

    @Test
    void unknownAgentFailsAndStillTearsDownItsWorktree() throws IOException {
        // Mirrors the real 'frog' smoke-test failure: an agent with no registered
        // backend. The lookup happens AFTER worktree creation, so cleanup must run.
        Path chatmapDir = projectDir("chatmap");
        Path task = writeTask(chatmapDir, "frog-task.md", "frog", "feature-smoke-test", "ribbit");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        HandoffRunResult result = results.get(0);
        assertEquals(HandoffRunResult.Outcome.failure, result.outcome());
        assertTrue(result.detail().contains("No configured model target for agent 'frog'"), result.detail());
        assertTrue(Files.exists(task), "a failed task's source file must not be archived away");
        assertTrue(executor.calledWithPrefix("git worktree remove --force"),
                "the worktree created before the failed agent lookup must still be cleaned up");
    }

    @Test
    void unknownAgentNeverInvokesAnyAgentProcess() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        writeTask(chatmapDir, "frog-task.md", "frog", "feature-x", "ribbit");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        service.processInboxOnce(inbox);

        assertFalse(executor.calledWithPrefix("claude -p"));
        assertFalse(executor.calledWithPrefix("codex"));
        assertFalse(executor.calledWithPrefix("frog"),
                "an unregistered agent name must never be shelled out to directly");
    }

    @Test
    void differentAgentsRouteToTheirOwnBackendInOneRun() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        writeTask(chatmapDir, "a-claude.md", "claude", "feature-cl", "via claude");
        writeTask(chatmapDir, "b-codex.md", "codex", "feature-cx", "via codex");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(""));
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        service.processInboxOnce(inbox);

        assertTrue(executor.calledWithPrefix("claude -p"), "the claude task should invoke the claude binary");
        assertTrue(executor.calledWithPrefix("codex"), "the codex task should invoke the codex binary");
    }

    @Test
    void claudeAndCodexUseTheirOwnUnattendedPermissionSyntax() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        writeTask(chatmapDir, "a-claude.md", "claude", "feature-cl", "x");
        writeTask(chatmapDir, "b-codex.md", "codex", "feature-cx", "y");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(""));
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        service.processInboxOnce(inbox);

        boolean claudeHasSkip = executor.calls().stream().anyMatch(c ->
                c.command().contains("claude") && c.command().contains("--dangerously-skip-permissions"));
        boolean codexHasWorkspaceWrite = executor.calls().stream().anyMatch(c ->
                c.command().contains("codex") && c.command().contains("--sandbox")
                        && c.command().contains("workspace-write"));
        assertTrue(claudeHasSkip, "claude invocation should carry the unrestricted-permission flag");
        assertTrue(codexHasWorkspaceWrite, "codex invocation should carry its own workspace-write sandbox flag");
    }

    // ---- eligibility boundaries -------------------------------------------

    @Test
    void emptyInboxProducesNoResults() throws IOException {
        projectDir("chatmap"); // exists but empty
        FakeCommandExecutor executor = new FakeCommandExecutor();
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertTrue(results.isEmpty());
        assertFalse(executor.calledWithPrefix("git worktree"));
    }

    @Test
    void nonMarkdownFilesAreNotTreatedAsTasks() throws IOException {
        Path chatmapDir = projectDir("chatmap");
        Files.writeString(chatmapDir.resolve("notes.txt"), "not a task");
        Files.writeString(chatmapDir.resolve("data.json"), "{}");
        Files.writeString(chatmapDir.resolve("README"), "nope");
        writeTask(chatmapDir, "real.md", "claude", "feature-r", "the only real one");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git status --porcelain", ok(""));
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertEquals(1, results.size());
        assertEquals("real.md", results.get(0).sourceFile().getFileName().toString());
    }

    @Test
    void hiddenDotFolderIsNeverScanned() throws IOException {
        // A '.test' folder of sample handoffs must be inert -- the scanner skips
        // dot-prefixed project folders exactly as it skips '.archive'.
        Path dotTest = inbox.resolve(".test");
        Files.createDirectories(dotTest);
        writeTask(dotTest, "sample.md", "claude", "feature-x", "should never run");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertTrue(results.isEmpty(), "task files under a dot-prefixed folder must not be picked up");
    }

    @Test
    void filesDirectlyInInboxRootAreIgnored() throws IOException {
        // Only project *subfolders* are scanned; a stray .md at the inbox root
        // has no project key and must not be processed.
        writeTask(inbox, "stray.md", "claude", "feature-x", "orphan at root");
        projectDir("chatmap");
        FakeCommandExecutor executor = new FakeCommandExecutor();
        HandoffOrchestratorService service = newService(
                executor, Map.of("chatmap", Path.of("fake-target-repo")), false);

        List<HandoffRunResult> results = service.processInboxOnce(inbox);

        assertTrue(results.isEmpty(), "a task file at the inbox root belongs to no project and must be ignored");
    }

    // ---- helpers ----------------------------------------------------------

    private static CommandResult ok() {
        return ok("");
    }

    private static CommandResult ok(String stdout) {
        return new CommandResult(0, stdout, "", Duration.ofMillis(10), false);
    }

    /**
     * Records every request and returns a scripted result for the first
     * matching command-prefix key. Adds {@code respondOnce} to the sibling
     * suite's fake so a prefix can fail exactly once and then behave normally
     * -- needed to fail one task among several without failing all of them.
     */
    private static final class FakeCommandExecutor implements CommandExecutor {
        private final List<CommandRequest> calls = new ArrayList<>();
        private final Map<String, CommandResult> responses = new LinkedHashMap<>();
        private final Map<String, CommandResult> onceResponses = new LinkedHashMap<>();

        void respond(String commandPrefix, CommandResult result) {
            responses.put(commandPrefix, result);
        }

        void respondOnce(String commandPrefix, CommandResult result) {
            onceResponses.put(commandPrefix, result);
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
            for (Map.Entry<String, CommandResult> entry : onceResponses.entrySet()) {
                if (key.startsWith(entry.getKey())) {
                    CommandResult result = entry.getValue();
                    onceResponses.remove(entry.getKey());
                    return withRequestPaths(result, request);
                }
            }
            for (Map.Entry<String, CommandResult> entry : responses.entrySet()) {
                if (key.startsWith(entry.getKey())) {
                    return withRequestPaths(entry.getValue(), request);
                }
            }
            return withRequestPaths(ok(), request);
        }

        private static CommandResult withRequestPaths(CommandResult result, CommandRequest request) {
            if (request.stdoutPath() == null && request.stderrPath() == null) {
                return result;
            }
            return new CommandResult(result.exitCode(), result.standardOutput(), result.standardError(),
                    result.duration(), result.timedOut(), result.standardOutputTruncated(),
                    result.standardErrorTruncated(), request.stdoutPath(), request.stderrPath());
        }
    }
}
