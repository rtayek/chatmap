package chatmap.application.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import chatmap.application.port.command.CommandResult;
import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.application.port.handoff.HandoffFileStoreException;
import chatmap.application.support.Log;
import chatmap.domain.HandoffTask;

public final class HandoffOrchestratorService {

    private static final Logger LOG = Log.of(HandoffOrchestratorService.class);

    private final Map<ProviderId, LlmProvider> providers;
    private final Map<String, ModelTarget> agentTargets;
    private final HandoffFileStore fileStore;
    private final Map<String, Path> projectRegistry;
    private final boolean autoPush;
    
    private final GitWorkspaceManager gitManager;
    private final HandoffInboxManager inboxManager;

    public HandoffOrchestratorService(
            CommandExecutor commandExecutor,
            Map<ProviderId, LlmProvider> providers,
            Map<String, ModelTarget> agentTargets,
            HandoffFileStore fileStore,
            Map<String, Path> projectRegistry,
            Clock clock,
            boolean autoPush) {
        this.providers = Map.copyOf(Objects.requireNonNull(providers, "providers"));
        this.agentTargets = Map.copyOf(Objects.requireNonNull(agentTargets, "agentTargets"));
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        this.projectRegistry = Map.copyOf(Objects.requireNonNull(projectRegistry, "projectRegistry"));
        this.autoPush = autoPush;
        
        this.gitManager = new GitWorkspaceManager(Objects.requireNonNull(commandExecutor, "commandExecutor"), this.fileStore);
        this.inboxManager = new HandoffInboxManager(this.fileStore, Objects.requireNonNull(clock, "clock"));
    }

    public List<HandoffRunResult> processInboxOnce(Path inboxRepo) {
        Objects.requireNonNull(inboxRepo, "inboxRepo");
        LOG.info("Executing handoff orchestrator check on {}", inboxRepo);
        
        CommandResult pullResult = gitManager.pull(inboxRepo);
        if (pullResult.exitCode() != 0) {
            LOG.warn("git pull failed for inbox {}: {}", inboxRepo, pullResult.standardError().strip());
            return List.of();
        }

        List<Path> taskFiles = inboxManager.scanForTaskFiles(inboxRepo);
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

    private HandoffRunResult processOne(Path inboxRepo, Path file) {
        String projectKey = inboxManager.projectKeyOf(file);
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
            CommandResult worktreeResult = gitManager.addWorktree(targetRepo, worktree, task.branch());
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
            Path agentStdout = inboxManager.agentOutputPath(file, "stdout.log");
            Path agentStderr = inboxManager.agentOutputPath(file, "stderr.log");
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
        } catch (GitWorkspaceManager.WorktreeCommitFailedException commitFailure) {
            preserveWorktree = true;
            return recordFailure(inboxRepo, file, projectKey,
                    commitFailure.getMessage() + " Worktree preserved at " + worktree + " for manual recovery.");
        } catch (CommandExecutionException executionFailure) {
            LOG.warn("Could not run a required git command for {}: {}", file, executionFailure.getMessage());
            return recordFailure(inboxRepo, file, projectKey,
                    "Could not run a required git command: " + executionFailure.getMessage());
        } catch (GitWorkspaceManager.GitCommandFailedException gitFailure) {
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
                gitManager.removeWorktree(targetRepo, worktree);
                LOG.debug("Removed worktree {}", worktree);
            }
        }
    }

    private void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new HandoffFileStoreException("Could not create directory " + directory, failure);
        }
    }

    private HandoffRunResult recordSuccess(Path inboxRepo, Path file, HandoffTask task, Path worktree,
            CommandResult agentResult) {
        boolean worktreeHasChanges = gitManager.hasChanges(worktree);
        boolean pushPending = false;
        if (worktreeHasChanges) {
            gitManager.requireGitSuccess("git add failed in worktree", gitManager.addAll(worktree));
            CommandResult commitResult = gitManager.commit(worktree, "Handoff: " + task.branch());
            if (commitResult.exitCode() != 0) {
                throw new GitWorkspaceManager.WorktreeCommitFailedException("git commit failed in worktree for branch "
                        + task.branch() + ": " + commitResult.standardError().strip());
            }
            if (autoPush) {
                CommandResult pushResult = gitManager.pushUpstream(worktree, task.branch());
                if (pushResult.exitCode() != 0) {
                    pushPending = true;
                    LOG.warn("git push failed in worktree for branch {}: {}", task.branch(),
                            gitManager.commandFailureDetail(pushResult));
                }
            } else {
                pushPending = true;
            }
        }

        Path archived = inboxManager.archiveTask(file);
        Path resultFile = inboxManager.writeResultFile(archived, task, agentResult);
        gitManager.gitAddPaths(inboxRepo, file, archived, resultFile,
                agentResult.standardOutputPath(), agentResult.standardErrorPath());
        
        CommandResult archiveCommitResult = gitManager.commit(inboxRepo, "Archive completed handoff: " + GitWorkspaceManager.relativeName(inboxRepo, archived));
        boolean archiveCommitFailed = archiveCommitResult.exitCode() != 0;
        if (archiveCommitFailed) {
            pushPending = true;
        } else if (autoPush) {
            CommandResult pushResult = gitManager.push(inboxRepo);
            pushPending = pushPending || pushResult.exitCode() != 0;
        } else {
            pushPending = true;
        }

        String detail;
        if (archiveCommitFailed) {
            detail = "Agent completed and task archived to " + GitWorkspaceManager.relativeName(inboxRepo, archived)
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

    private HandoffRunResult recordFailure(Path inboxRepo, Path file, String projectKey, String reason) {
        return recordFailure(inboxRepo, file, projectKey, reason, null);
    }

    private HandoffRunResult recordFailure(Path inboxRepo, Path file, String projectKey, String reason,
            CommandResult agentResult) {
        LOG.warn("Handoff task {} failed: {}", file, reason);
        Path reportPath;
        try {
            reportPath = inboxManager.writeFailureReport(file, projectKey, reason, agentResult, inboxRepo);
        } catch (HandoffFileStoreException writeFailure) {
            return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure,
                    reason + " (additionally, could not write failure report: " + writeFailure.getMessage() + ")",
                    false);
        }

        CommandResult addResult = agentResult == null
                ? gitManager.gitAddPaths(inboxRepo, reportPath)
                : gitManager.gitAddPaths(inboxRepo, reportPath, agentResult.standardOutputPath(), agentResult.standardErrorPath());
        if (addResult.exitCode() != 0) {
            return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure,
                    reason + " (additionally, could not stage failure report: "
                            + gitManager.commandFailureDetail(addResult) + ")",
                    false);
        }
        CommandResult commitResult = gitManager.commit(inboxRepo, "Add failure report for: " + GitWorkspaceManager.relativeName(inboxRepo, file));
        if (commitResult.exitCode() != 0) {
            return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure,
                    reason + " (additionally, could not commit failure report: "
                            + gitManager.commandFailureDetail(commitResult) + ")",
                    false);
        }
        boolean pushPending = true;
        if (autoPush) {
            CommandResult pushResult = gitManager.push(inboxRepo);
            pushPending = pushResult.exitCode() != 0;
            if (pushPending) {
                LOG.warn("git push failed for inbox failure report {}: {}", reportPath,
                        gitManager.commandFailureDetail(pushResult));
            }
        }
        return new HandoffRunResult(file, projectKey, HandoffRunResult.Outcome.failure, reason, pushPending);
    }
}
