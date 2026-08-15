package chatmap.application.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;

import chatmap.application.port.command.CommandExecutionException;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;
import chatmap.application.support.Log;
import chatmap.domain.HandoffTask;

/**
 * Polls a Git "inbox" repository for handoff task markdown files, runs each
 * one against an isolated worktree of its target project using the
 * requested local CLI agent, and reports success (archiving the task file)
 * or failure (writing a failure report back into the inbox) so results sync
 * back through the inbox repo.
 *
 * <p><b>Design decisions made without further confirmation, per this
 * feature's own "decide and document" instruction:</b>
 * <ul>
 *   <li><b>Push follows the caller's {@code autoPush} flag.</b> The CLI
 *   defaults this to on when driven by its properties-file config (opt
 *   back out with {@code autoPush=false} there), and to off for explicit
 *   runs without {@code --auto-push}. Either way, every run commits
 *   locally in both the target project's worktree and the inbox repo
 *   regardless of the push setting. Each {@link HandoffRunResult#pushPending()}
 *   flags whether that commit still needs pushing, so a caller can
 *   surface it clearly rather than silently.</li>
 *   <li><b>Worktree branch handling.</b> {@code git worktree add} is tried
 *   first assuming the branch already exists (checked via
 *   {@code git show-ref}); otherwise it's created fresh off the target
 *   repo's current HEAD with {@code -b}, since the inbox template's
 *   {@code branch: feature-} suggests branches are typically new,
 *   per-task names. If the worktree's own commit fails, the worktree is
 *   preserved on disk (not force-removed) so the agent's uncommitted work
 *   can be recovered manually.</li>
 *   <li><b>"Remove or archive the source handoff file" -&gt; archive.</b> On
 *   success the task file is moved to an {@code archive/} subfolder inside
 *   its same project folder in the inbox repo (matching this project's own
 *   {@code handoffs/archive/} convention) rather than deleted outright, so a
 *   processed task remains auditable.</li>
 *   <li><b>Agent invocation shape.</b> Mirrors the existing
 *   {@code StandardCliBackend} convention: {@code <agent> -p} with the task
 *   body piped as standard input, run inside the worktree directory. For
 *   {@code claude} specifically, also passes
 *   {@code --dangerously-skip-permissions} -- confirmed live that without
 *   it, {@code claude -p} exits 0 and makes zero file edits (every task
 *   "succeeds" but does nothing), which defeats the entire feature. This
 *   trades a real security boundary (interactive permission prompts) for
 *   the worktree's isolation instead: a task file landing in the inbox now
 *   gets unrestricted tool access, contained only in that a disposable
 *   branch/worktree is what it can affect, not that its actions are
 *   individually confirmed. See {@link #agentCommand}.</li>
 * </ul>
 */
public final class HandoffOrchestratorService {

    private static final Logger LOG = Log.of(HandoffOrchestratorService.class);
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration AGENT_TIMEOUT = Duration.ofMinutes(30);
    private static final String ARCHIVE_SUBDIR = ".archive";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final CommandExecutor commandExecutor;
    private final Map<String, Path> projectRegistry;
    private final Clock clock;
    private final boolean autoPush;

    public HandoffOrchestratorService(
            CommandExecutor commandExecutor,
            Map<String, Path> projectRegistry,
            Clock clock,
            boolean autoPush) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.projectRegistry = Map.copyOf(Objects.requireNonNull(projectRegistry, "projectRegistry"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.autoPush = autoPush;
    }

    /**
     * Pulls the inbox, finds every eligible task file, and processes each one
     * in turn. Aborts (returning no results) if the pull itself fails --
     * scanning and committing against a stale or conflicted inbox tree would
     * risk baking a bad merge state into the next commit.
     */
    public List<HandoffRunResult> processInboxOnce(Path inboxRepo) {
        Objects.requireNonNull(inboxRepo, "inboxRepo");
        LOG.info("Executing handoff orchestrator check on {}", inboxRepo);
        CommandResult pullResult = git(inboxRepo, "pull");
        if (pullResult.exitCode() != 0) {
            LOG.warn("git pull failed for inbox {}: {}", inboxRepo, pullResult.standardError().strip());
            return List.of();
        }

        List<Path> taskFiles = scanForTaskFiles(inboxRepo);
        LOG.info("Found {} handoff task file(s) in {}", taskFiles.size(), inboxRepo);

        List<HandoffRunResult> results = new ArrayList<>();
        for (Path file : taskFiles) {
            results.add(processOne(inboxRepo, file));
        }
        long succeeded = results.stream().filter(r -> r.outcome() == HandoffRunResult.Outcome.success).count();
        LOG.info("Handoff orchestrator check complete: {} succeeded, {} failed", succeeded,
                results.size() - succeeded);
        return results;
    }

    /**
     * Scans the inbox's immediate project subfolders for {@code .md} files,
     * ignoring {@code template.md} (case-insensitive) and anything already
     * under an {@value #ARCHIVE_SUBDIR} folder. Deterministic order (sorted
     * by path) so a run's processing order doesn't depend on filesystem
     * iteration order.
     */
    
    static List<Path> scanForTaskFiles(Path inboxRepo) {
        try (var projectDirs = Files.list(inboxRepo)) {
            List<Path> files = new ArrayList<>();
            for (Path projectDir : projectDirs.filter(Files::isDirectory)
                    .filter(HandoffOrchestratorService::isNotHidden).toList()) {
                try (var entries = Files.list(projectDir)) {
                    entries.filter(Files::isRegularFile)
                            .filter(HandoffOrchestratorService::isEligibleTaskFile)
                            .forEach(files::add);
                }
            }
            files.sort(Comparator.comparing(Path::toString));
            return files;
        } catch (IOException failure) {
            throw new UncheckedIoOrchestratorException("Could not scan inbox " + inboxRepo, failure);
        }
    }

    /** Every file passed here comes from {@link #scanForTaskFiles}, so both levels always exist in practice. */
    private static String projectKeyOf(Path file) {
        Path parent = file.getParent();
        Path parentName = parent == null ? null : parent.getFileName();
        return parentName == null ? "unknown" : parentName.toString();
    }

    private static boolean isNotHidden(Path dir) {
        Path name = dir.getFileName();
        return name != null && !name.toString().startsWith(".");
    }

    private static boolean isEligibleTaskFile(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".md")
                && !name.equalsIgnoreCase("template.md")
                && !name.startsWith("failure-report-");
    }
    
    private HandoffRunResult processOne(Path inboxRepo, Path file) {
        String projectKey = projectKeyOf(file);
        LOG.info("Processing handoff task {} (project={})", file, projectKey);

        HandoffTask task;
        try {
            task = HandoffTaskParser.parse(file, projectKey, Files.readString(file));
        } catch (HandoffTaskParseException | IOException parseFailure) {
            LOG.warn("Could not parse handoff file {}: {}", file, parseFailure.getMessage());
            return recordFailure(inboxRepo, file, projectKey,
                    "Could not parse handoff file: " + parseFailure.getMessage());
        }

        Path targetRepo = projectRegistry.get(projectKey);
        if (targetRepo == null) {
            LOG.warn("No configured target project path for project key '{}'", projectKey);
            return recordFailure(inboxRepo, file, projectKey,
                    "No configured target project path for project key '" + projectKey + "'.");
        }

        Path worktree = allocateWorktreePath(task.branch());
        LOG.debug("Allocated worktree {} for branch {} of {}", worktree, task.branch(), targetRepo);
        boolean preserveWorktree = false;
        try {
            CommandResult worktreeResult = addWorktree(targetRepo, worktree, task.branch());
            if (worktreeResult.exitCode() != 0) {
                LOG.warn("git worktree add failed for {}: {}", targetRepo, worktreeResult.standardError().strip());
                return recordFailure(inboxRepo, file, projectKey,
                        "git worktree add failed: " + worktreeResult.standardError().strip());
            }

            LOG.info("Running agent '{}' on branch {} in {}", task.agent(), task.branch(), worktree);
            CommandResult agentResult = commandExecutor.run(new CommandRequest(
                    agentCommand(task.agent()), task.body(), AGENT_TIMEOUT, worktree));
            if (agentResult.timedOut()) {
                LOG.warn("{} timed out after {} on {}", task.agent(), AGENT_TIMEOUT, file);
                return recordFailure(inboxRepo, file, projectKey,
                        task.agent() + " timed out after " + AGENT_TIMEOUT, agentResult);
            }
            if (agentResult.exitCode() != 0) {
                LOG.warn("{} exited with status {} on {}", task.agent(), agentResult.exitCode(), file);
                return recordFailure(inboxRepo, file, projectKey,
                        task.agent() + " exited with status " + agentResult.exitCode()
                        + (agentResult.standardError().isBlank() ? "" : ": " + agentResult.standardError().strip()),
                        agentResult);
            }

            return recordSuccess(inboxRepo, file, task, worktree, agentResult);
        } catch (WorktreeCommitFailedException commitFailure) {
            // The agent's edits are sitting uncommitted in the worktree -- force-removing
            // it here would destroy them permanently, so leave it for manual recovery.
            preserveWorktree = true;
            return recordFailure(inboxRepo, file, projectKey,
                    commitFailure.getMessage() + " Worktree preserved at " + worktree + " for manual recovery.");
        } catch (CommandExecutionException executionFailure) {
            LOG.warn("Could not run {} for {}: {}", task.agent(), file, executionFailure.getMessage());
            return recordFailure(inboxRepo, file, projectKey,
                    "Could not run " + task.agent() + ": " + executionFailure.getMessage());
        } catch (UncheckedIoOrchestratorException archiveFailure) {
            LOG.warn("Could not finalize handoff task {}: {}", file, archiveFailure.getMessage());
            return recordFailure(inboxRepo, file, projectKey,
                    "Could not finalize handoff task: " + archiveFailure.getMessage());
        } finally {
            if (preserveWorktree) {
                LOG.warn("Preserving worktree {} instead of removing it so the commit failure can be recovered manually",
                        worktree);
            } else {
                removeWorktree(targetRepo, worktree);
                LOG.debug("Removed worktree {}", worktree);
            }
        }
    }

    private Path allocateWorktreePath(String branch) {
        try {
            Path placeholder = Files.createTempDirectory("chatmap-handoff-" + sanitize(branch) + "-");
            // git worktree add requires the target path not already exist.
            Files.delete(placeholder);
            return placeholder;
        } catch (IOException failure) {
            throw new UncheckedIoOrchestratorException("Could not allocate a worktree path", failure);
        }
    }

    /**
     * The CLI invocation for a task's agent. For {@code claude} this adds
     * {@code --dangerously-skip-permissions} -- confirmed live on
     * 2026-08-12 that without it, {@code claude -p} runs and exits 0 but
     * makes no file edits at all (every prior task "succeeded" with zero
     * changes), so the task is otherwise unable to do anything besides talk.
     * It also requests {@code --output-format stream-json}, which requires
     * {@code --verbose} or the CLI rejects the invocation outright.
     * Deliberately scoped to {@code claude} only: the flag names and
     * semantics for other agents (codex, antigravity, ...) haven't been
     * verified against their {@code --help} output for the invocation shape
     * used here ({@code <agent> -p}), and passing an unrecognized flag to a
     * different CLI would just trade one silent no-op failure mode for a
     * loud one.
     */
    static List<String> agentCommand(String agent) {
        if ("claude".equals(agent)) {
            return List.of(agent, "-p", "--dangerously-skip-permissions",
                    "--output-format", "stream-json", "--verbose");
        }
        return List.of(agent, "-p");
    }

    private static String sanitize(String branch) {
        return branch.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private CommandResult addWorktree(Path targetRepo, Path worktree, String branch) {
        CommandResult branchExists = git(targetRepo, "show-ref", "--verify", "--quiet", "refs/heads/" + branch);
        if (branchExists.exitCode() == 0) {
            return git(targetRepo, "worktree", "add", worktree.toString(), branch);
        }
        return git(targetRepo, "worktree", "add", "-b", branch, worktree.toString());
    }

    private void removeWorktree(Path targetRepo, Path worktree) {
        git(targetRepo, "worktree", "remove", "--force", worktree.toString());
        try {
            if (Files.exists(worktree)) {
                deleteRecursively(worktree);
            }
        } catch (IOException ignored) {
            // Best-effort: a leftover temp directory is not worth failing the run over.
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private HandoffRunResult recordSuccess(Path inboxRepo, Path file, HandoffTask task, Path worktree,
            CommandResult agentResult) {
        boolean worktreeHasChanges = hasChanges(worktree);
        boolean pushPending = false;
        if (worktreeHasChanges) {
            git(worktree, "add", "-A");
            CommandResult commitResult = git(worktree, "commit", "-m", "Handoff: " + task.branch());
            if (commitResult.exitCode() != 0) {
                throw new WorktreeCommitFailedException("git commit failed in worktree for branch "
                        + task.branch() + ": " + commitResult.standardError().strip());
            }
            if (autoPush) {
                CommandResult pushResult = git(worktree, "push", "-u", "origin", task.branch());
                pushPending = pushResult.exitCode() != 0;
            } else {
                pushPending = true;
            }
        }

        Path archived = archive(file);
        writeResultFile(archived, task, agentResult);
        git(inboxRepo, "add", "-A");
        CommandResult archiveCommitResult = git(inboxRepo, "commit", "-m",
                "Archive completed handoff: " + relativeName(inboxRepo, archived));
        boolean archiveCommitFailed = archiveCommitResult.exitCode() != 0;
        if (archiveCommitFailed) {
            // The task file is already moved on disk with no clean way to undo that,
            // so this stays a success -- but flag it loudly rather than silently
            // leaving an uncommitted archive sitting in the inbox working tree.
            pushPending = true;
        } else if (autoPush) {
            CommandResult pushResult = git(inboxRepo, "push");
            pushPending = pushPending || pushResult.exitCode() != 0;
        } else {
            pushPending = true;
        }

        String detail;
        if (archiveCommitFailed) {
            detail = "Agent completed and task archived to " + relativeName(inboxRepo, archived)
                    + ", but committing the archive to the inbox failed: "
                    + archiveCommitResult.standardError().strip();
            LOG.warn("Handoff task {} partially succeeded ({})", file, detail);
        } else {
            detail = worktreeHasChanges
                    ? "Agent completed and committed changes on branch " + task.branch() + "."
                    : "Agent completed successfully but left no changes to commit.";
            LOG.info("Handoff task {} succeeded ({})", file, detail);
        }
        return new HandoffRunResult(file, task.projectKey(), HandoffRunResult.Outcome.success, detail, pushPending);
    }

    /**
     * Writes the agent's full stdout as a sibling of the archived task file
     * (e.g. {@code test-6.md} -&gt; {@code test-6.result.md} in the same
     * {@value #ARCHIVE_SUBDIR} folder), so the task and its result archive
     * and sync together.
     */
    private void writeResultFile(Path archivedTaskFile, HandoffTask task, CommandResult agentResult) {
        Path resultPath = resultFilePath(archivedTaskFile);
        String content = "# Agent Result: " + archivedTaskFile.getFileName() + "\n\n"
                + "- Project: " + task.projectKey() + "\n"
                + "- Agent: " + task.agent() + "\n"
                + "- Branch: " + task.branch() + "\n"
                + "- Timestamp: " + clock.instant() + "\n"
                + "- Exit code: " + agentResult.exitCode() + "\n\n"
                + "## Output\n\n"
                + agentResult.standardOutput();
        try {
            Files.writeString(resultPath, content);
        } catch (IOException failure) {
            // Best-effort: the task itself already succeeded, so a result-file write
            // failure shouldn't take down the whole poll run -- log and move on.
            LOG.warn("Could not write agent result file {}: {}", resultPath, failure.getMessage());
        }
    }

    private static Path resultFilePath(Path archivedTaskFile) {
        Path fileName = archivedTaskFile.getFileName();
        String baseName = (fileName == null ? "task" : fileName.toString()).replaceFirst("(?i)\\.md$", "");
        return archivedTaskFile.resolveSibling(baseName + ".result.md");
    }

    private HandoffRunResult recordFailure(Path inboxRepo, Path file, String projectKey, String reason) {
        return recordFailure(inboxRepo, file, projectKey, reason, null);
    }

    private HandoffRunResult recordFailure(Path inboxRepo, Path file, String projectKey, String reason,
            CommandResult agentResult) {
        LOG.warn("Handoff task {} failed: {}", file, reason);
        Path reportPath = failureReportPath(file);
        String report = "# Handoff Failure Report\n\n"
                + "- Source file: " + relativeName(inboxRepo, file) + "\n"
                + "- Project: " + projectKey + "\n"
                + "- Timestamp: " + clock.instant() + "\n\n"
                + "## Reason\n\n"
                + reason + "\n";
        if (agentResult != null) {
            report += "\n## Agent Output\n\n" + agentResult.standardOutput() + "\n";
        }
        try {
            Files.writeString(reportPath, report);
        } catch (IOException writeFailure) {
            // The failure report itself couldn't be written; the original reason is still
            // the useful signal here, so surface both rather than throwing past the caller.
            return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure,
                    reason + " (additionally, could not write failure report: " + writeFailure.getMessage() + ")",
                    false);
        }

        git(inboxRepo, "add", "-A");
        git(inboxRepo, "commit", "-m", "Add failure report for: " + relativeName(inboxRepo, file));
        boolean pushPending = true;
        if (autoPush) {
            CommandResult pushResult = git(inboxRepo, "push");
            pushPending = pushResult.exitCode() != 0;
        }
        return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure, reason, pushPending);
    }

    private Path failureReportPath(Path taskFile) {
        String stamp = TIMESTAMP.format(clock.instant().atZone(java.time.ZoneOffset.UTC));
        Path fileName = taskFile.getFileName();
        String baseName = (fileName == null ? "task" : fileName.toString()).replaceFirst("(?i)\\.md$", "");
        return taskFile.resolveSibling("failure-report-" + baseName + "-" + stamp + ".md");
    }

    private Path archive(Path file) {
        Path parent = file.getParent();
        Path fileName = file.getFileName();
        if (parent == null || fileName == null) {
            throw new UncheckedIoOrchestratorException(
                    "File has no parent directory or file name to archive: " + file,
                    new IOException("not archivable: " + file));
        }
        try {
            Path archiveDir = parent.resolve(ARCHIVE_SUBDIR);
            Files.createDirectories(archiveDir);
            Path destination = archiveDir.resolve(fileName);
            Files.move(file, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException failure) {
            throw new UncheckedIoOrchestratorException("Could not archive " + file, failure);
        }
    }

    private static String relativeName(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException notRelativizable) {
            return file.toString();
        }
    }

    private boolean hasChanges(Path worktree) {
        CommandResult status = git(worktree, "status", "--porcelain");
        return !status.standardOutput().isBlank();
    }

    private CommandResult git(Path workingDirectory, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return commandExecutor.run(new CommandRequest(command, "", GIT_TIMEOUT, workingDirectory));
    }

    /** Wraps an unexpected {@link IOException} from filesystem scanning/cleanup as unchecked. */
    static final class UncheckedIoOrchestratorException extends RuntimeException {
        UncheckedIoOrchestratorException(String message, IOException cause) {
            super(message, cause);
        }
    }

    /** Signals a worktree commit failure so {@link #processOne} can preserve the worktree instead of destroying it. */
    static final class WorktreeCommitFailedException extends RuntimeException {
        WorktreeCommitFailedException(String message) {
            super(message);
        }
    }
}
