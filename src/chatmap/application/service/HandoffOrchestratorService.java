package chatmap.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;

import chatmap.application.port.llm.LlmBackendExecutionException;
import chatmap.application.port.llm.LlmBackendStartupException;
import chatmap.application.port.llm.LlmCapability;
import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.LlmRequest;
import chatmap.application.port.llm.CommandBackedLlmProvider;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.OutputFormat;
import chatmap.application.port.llm.PermissionMode;
import chatmap.application.port.llm.ProviderId;
import chatmap.application.port.command.CommandExecutionException;
import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;
import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.application.port.handoff.HandoffFileStoreException;
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
 *   <li><b>Agent invocation goes through {@link CommandBackedLlmProvider}.</b>
 *   The task body is piped as the prompt, with {@code workingDirectory} set
 *   to the worktree and {@code permissionMode} set to
 *   {@link PermissionMode#unrestricted unrestricted} -- confirmed live that
 *   without an unattended-permissions flag, {@code claude -p} exits 0 and
 *   makes zero file edits (every task "succeeds" but does nothing), which
 *   defeats the entire feature. This trades a real security boundary
 *   (interactive permission prompts) for the worktree's isolation instead:
 *   a task file landing in the inbox now gets unrestricted tool access,
 *   contained only in that a disposable branch/worktree is what it can
 *   affect, not that its actions are individually confirmed. Exactly which
 *   flags that translates to is each provider's own concern.</li>
 * </ul>
 */
public final class HandoffOrchestratorService {

    private static final Logger LOG = Log.of(HandoffOrchestratorService.class);
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);
    private static final String ARCHIVE_SUBDIR = ".archive";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final CommandExecutor commandExecutor;
    private final Map<ProviderId, LlmProvider> providers;
    private final Map<String, ModelTarget> agentTargets;
    private final HandoffFileStore fileStore;
    private final Map<String, Path> projectRegistry;
    private final Clock clock;
    private final boolean autoPush;

    public HandoffOrchestratorService(
            CommandExecutor commandExecutor,
            Map<ProviderId, LlmProvider> providers,
            Map<String, ModelTarget> agentTargets,
            HandoffFileStore fileStore,
            Map<String, Path> projectRegistry,
            Clock clock,
            boolean autoPush) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.providers = Map.copyOf(Objects.requireNonNull(providers, "providers"));
        this.agentTargets = Map.copyOf(Objects.requireNonNull(agentTargets, "agentTargets"));
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
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

        List<Path> taskFiles = scanForTaskFiles(inboxRepo, fileStore);
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
    
    static List<Path> scanForTaskFiles(Path inboxRepo, HandoffFileStore fileStore) {
        List<Path> files = new ArrayList<>();
        for (Path projectDir : fileStore.listDirectories(inboxRepo).stream()
                .filter(HandoffOrchestratorService::isNotHidden).toList()) {
            fileStore.listFiles(projectDir).stream()
                    .filter(HandoffOrchestratorService::isEligibleTaskFile)
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::toString));
        return files;
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
            task = HandoffTaskParser.parse(file, projectKey, fileStore.readString(file));
        } catch (HandoffTaskParseException | HandoffFileStoreException parseFailure) {
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

        Path worktree = fileStore.allocateWorktreeDirectory(task.branch());
        LOG.debug("Allocated worktree {} for branch {} of {}", worktree, task.branch(), targetRepo);
        boolean preserveWorktree = false;
        try {
            CommandResult worktreeResult = addWorktree(targetRepo, worktree, task.branch());
            if (worktreeResult.exitCode() != 0) {
                LOG.warn("git worktree add failed for {}: {}", targetRepo, worktreeResult.standardError().strip());
                return recordFailure(inboxRepo, file, projectKey,
                        "git worktree add failed: " + worktreeResult.standardError().strip());
            }

            ModelTarget target = agentTargets.get(task.agent());
            if (target == null) {
                LOG.warn("No configured model target for agent '{}'", task.agent());
                return recordFailure(inboxRepo, file, projectKey,
                        "No configured model target for agent '" + task.agent() + "'.");
            }
            LlmProvider provider = providers.get(target.providerId());
            if (!(provider instanceof CommandBackedLlmProvider backend)) {
                LOG.warn("No command-backed LLM provider for agent '{}'", task.agent());
                return recordFailure(inboxRepo, file, projectKey,
                        "No command-backed LLM provider for agent '" + task.agent() + "'.");
            }

            LOG.info("Running agent '{}' on branch {} in {}", task.agent(), task.branch(), worktree);
            Path agentStdout = agentOutputPath(file, "stdout.log");
            Path agentStderr = agentOutputPath(file, "stderr.log");
            createDirectories(Objects.requireNonNull(agentStdout.getParent(), "agent output parent"));
            LlmRequest request = LlmRequest.of(task.body())
                    .withWorkingDirectory(worktree)
                    .withPermissionMode(PermissionMode.unrestricted)
                    .withOutputPaths(agentStdout, agentStderr);
            if (provider.capabilities(target).contains(LlmCapability.streamJson)) {
                request = request.withOutputFormat(OutputFormat.streamJson);
            }
            CommandResult agentResult;
            try {
                agentResult = backend.executeWithResult(target, request).commandResult();
            } catch (LlmBackendExecutionException executionFailure) {
                LOG.warn("{} for {}: {}", task.agent(), file, executionFailure.getMessage());
                return recordFailure(inboxRepo, file, projectKey, executionFailure.getMessage(),
                        executionFailure.commandResult().orElse(null));
            } catch (LlmBackendStartupException startupFailure) {
                LOG.warn("Could not run {} for {}: {}", task.agent(), file, startupFailure.getMessage());
                return recordFailure(inboxRepo, file, projectKey,
                        "Could not run " + task.agent() + ": " + startupFailure.getMessage());
            }

            return recordSuccess(inboxRepo, file, task, worktree, agentResult);
        } catch (WorktreeCommitFailedException commitFailure) {
            // The agent's edits are sitting uncommitted in the worktree -- force-removing
            // it here would destroy them permanently, so leave it for manual recovery.
            preserveWorktree = true;
            return recordFailure(inboxRepo, file, projectKey,
                    commitFailure.getMessage() + " Worktree preserved at " + worktree + " for manual recovery.");
        } catch (CommandExecutionException executionFailure) {
            LOG.warn("Could not run a required git command for {}: {}", file, executionFailure.getMessage());
            return recordFailure(inboxRepo, file, projectKey,
                    "Could not run a required git command: " + executionFailure.getMessage());
        } catch (GitCommandFailedException gitFailure) {
            LOG.warn("Git operation failed for {}: {}", file, gitFailure.getMessage());
            return recordFailure(inboxRepo, file, projectKey, gitFailure.getMessage());
        } catch (HandoffFileStoreException archiveFailure) {
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

    private CommandResult addWorktree(Path targetRepo, Path worktree, String branch) {
        CommandResult branchExists = git(targetRepo, "show-ref", "--verify", "--quiet", "refs/heads/" + branch);
        if (branchExists.exitCode() == 0) {
            return git(targetRepo, "worktree", "add", worktree.toString(), branch);
        }
        if (branchExists.exitCode() != 1) {
            throw new GitCommandFailedException("git show-ref failed: " + commandFailureDetail(branchExists));
        }
        return git(targetRepo, "worktree", "add", "-b", branch, worktree.toString());
    }

    private void removeWorktree(Path targetRepo, Path worktree) {
        git(targetRepo, "worktree", "remove", "--force", worktree.toString());
        fileStore.deleteRecursively(worktree);
    }

    private HandoffRunResult recordSuccess(Path inboxRepo, Path file, HandoffTask task, Path worktree,
            CommandResult agentResult) {
        boolean worktreeHasChanges = hasChanges(worktree);
        boolean pushPending = false;
        if (worktreeHasChanges) {
            requireGitSuccess("git add failed in worktree", git(worktree, "add", "-A"));
            CommandResult commitResult = git(worktree, "commit", "-m", "Handoff: " + task.branch());
            if (commitResult.exitCode() != 0) {
                throw new WorktreeCommitFailedException("git commit failed in worktree for branch "
                        + task.branch() + ": " + commitResult.standardError().strip());
            }
            if (autoPush) {
                CommandResult pushResult = git(worktree, "push", "-u", "origin", task.branch());
                if (pushResult.exitCode() != 0) {
                    pushPending = true;
                    LOG.warn("git push failed in worktree for branch {}: {}", task.branch(),
                            commandFailureDetail(pushResult));
                }
            } else {
                pushPending = true;
            }
        }

        Path archived = fileStore.archive(file, ARCHIVE_SUBDIR);
        Path resultFile = writeResultFile(archived, task, agentResult);
        gitAddPaths(inboxRepo, file, archived, resultFile,
                agentResult.standardOutputPath(), agentResult.standardErrorPath());
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
        return new HandoffRunResult(file, task.projectKey(), archiveCommitFailed
                ? HandoffRunResult.Outcome.partialFailure
                : HandoffRunResult.Outcome.success, detail, pushPending);
    }

    /**
     * Writes the agent's full stdout as a sibling of the archived task file
     * (e.g. {@code test-6.md} -&gt; {@code test-6.result.md} in the same
     * {@value #ARCHIVE_SUBDIR} folder), so the task and its result archive
     * and sync together.
     */
    private Path writeResultFile(Path archivedTaskFile, HandoffTask task, CommandResult agentResult) {
        Path resultPath = resultFilePath(archivedTaskFile);
        String content = "# Agent Result: " + archivedTaskFile.getFileName() + "\n\n"
                + "- Project: " + task.projectKey() + "\n"
                + "- Agent: " + task.agent() + "\n"
                + "- Branch: " + task.branch() + "\n"
                + "- Timestamp: " + clock.instant() + "\n"
                + "- Exit code: " + agentResult.exitCode() + "\n\n"
                + "- Full stdout: " + optionalRelativeName(archivedTaskFile.getParent(), agentResult.standardOutputPath()) + "\n"
                + "- Full stderr: " + optionalRelativeName(archivedTaskFile.getParent(), agentResult.standardErrorPath()) + "\n\n"
                + "## Output\n\n"
                + agentResult.standardOutput();
        try {
            fileStore.writeString(resultPath, content);
            return resultPath;
        } catch (HandoffFileStoreException failure) {
            // Best-effort: the task itself already succeeded, so a result-file write
            // failure shouldn't take down the whole poll run -- log and move on.
            LOG.warn("Could not write agent result file {}: {}", resultPath, failure.getMessage());
            return null;
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
            fileStore.writeString(reportPath, report);
        } catch (HandoffFileStoreException writeFailure) {
            // The failure report itself couldn't be written; the original reason is still
            // the useful signal here, so surface both rather than throwing past the caller.
            return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure,
                    reason + " (additionally, could not write failure report: " + writeFailure.getMessage() + ")",
                    false);
        }

        CommandResult addResult = agentResult == null
                ? gitAddPaths(inboxRepo, reportPath)
                : gitAddPaths(inboxRepo, reportPath, agentResult.standardOutputPath(), agentResult.standardErrorPath());
        if (addResult.exitCode() != 0) {
            return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure,
                    reason + " (additionally, could not stage failure report: "
                            + commandFailureDetail(addResult) + ")",
                    false);
        }
        CommandResult commitResult = git(inboxRepo, "commit", "-m", "Add failure report for: " + relativeName(inboxRepo, file));
        if (commitResult.exitCode() != 0) {
            return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure,
                    reason + " (additionally, could not commit failure report: "
                            + commandFailureDetail(commitResult) + ")",
                    false);
        }
        boolean pushPending = true;
        if (autoPush) {
            CommandResult pushResult = git(inboxRepo, "push");
            pushPending = pushResult.exitCode() != 0;
            if (pushPending) {
                LOG.warn("git push failed for inbox failure report {}: {}", reportPath,
                        commandFailureDetail(pushResult));
            }
        }
        return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure, reason, pushPending);
    }

    private Path failureReportPath(Path taskFile) {
        String stamp = TIMESTAMP.format(clock.instant().atZone(java.time.ZoneOffset.UTC));
        Path fileName = taskFile.getFileName();
        String baseName = (fileName == null ? "task" : fileName.toString()).replaceFirst("(?i)\\.md$", "");
        return taskFile.resolveSibling("failure-report-" + baseName + "-" + stamp + ".md");
    }

    private static String relativeName(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException notRelativizable) {
            return file.toString();
        }
    }

    private static String optionalRelativeName(Path root, Path file) {
        return file == null ? "(not captured)" : relativeName(root, file);
    }

    private static Path agentOutputPath(Path taskFile, String suffix) {
        Path fileName = taskFile.getFileName();
        String baseName = (fileName == null ? "task" : fileName.toString()).replaceFirst("(?i)\\.md$", "");
        Path taskParent = taskFile.getParent();
        Path parent = taskParent == null ? Path.of(".") : taskParent;
        return parent.resolve(ARCHIVE_SUBDIR).resolve(baseName + "." + suffix);
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new HandoffFileStoreException("Could not create directory " + directory, failure);
        }
    }

    private boolean hasChanges(Path worktree) {
        CommandResult status = git(worktree, "status", "--porcelain");
        requireGitSuccess("git status failed in worktree", status);
        return !status.standardOutput().isBlank();
    }

    private CommandResult gitAddPaths(Path repository, Path... paths) {
        List<String> command = new ArrayList<>();
        command.add("add");
        command.add("--");
        for (Path path : paths) {
            if (path != null) {
                command.add(relativeName(repository, path));
            }
        }
        return git(repository, command.toArray(String[]::new));
    }

    private static void requireGitSuccess(String operation, CommandResult result) {
        if (result.exitCode() != 0) {
            throw new GitCommandFailedException(operation + ": " + commandFailureDetail(result));
        }
    }

    private static String commandFailureDetail(CommandResult result) {
        String stderr = result.standardError().strip();
        if (stderr.isBlank()) {
            stderr = result.standardOutput().strip();
        }
        if (stderr.isBlank()) {
            stderr = "exit code " + result.exitCode();
        }
        return stderr;
    }

    private CommandResult git(Path workingDirectory, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return commandExecutor.run(new CommandRequest(command, "", GIT_TIMEOUT, workingDirectory));
    }

    /** Signals a worktree commit failure so {@link #processOne} can preserve the worktree instead of destroying it. */
    static final class WorktreeCommitFailedException extends RuntimeException {
        WorktreeCommitFailedException(String message) {
            super(message);
        }
    }

    static final class GitCommandFailedException extends RuntimeException {
        GitCommandFailedException(String message) {
            super(message);
        }
    }
}
