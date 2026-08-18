package chatmap.application.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import chatmap.application.port.command.CommandExecutor;
import chatmap.application.port.command.CommandRequest;
import chatmap.application.port.command.CommandResult;
import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.application.support.Log;

class GitWorkspaceManager {
    private static final Logger LOG = Log.of(GitWorkspaceManager.class);
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);
    private final CommandExecutor commandExecutor;
    private final HandoffFileStore fileStore;

    GitWorkspaceManager(CommandExecutor commandExecutor, HandoffFileStore fileStore) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
    }

    GitOutcome pull(Path repository) {
        return GitOutcome.of(git(repository, "pull"), this);
    }

    GitOutcome addWorktree(Path targetRepo, Path worktree, String branch) {
        CommandResult branchExists = git(targetRepo, "show-ref", "--verify", "--quiet", "refs/heads/" + branch);
        if (branchExists.exitCode() == 0) {
            return GitOutcome.of(git(targetRepo, "worktree", "add", worktree.toString(), branch), this);
        }
        if (branchExists.exitCode() != 1) {
            throw new GitCommandFailedException("git show-ref failed: " + commandFailureDetail(branchExists));
        }
        return GitOutcome.of(git(targetRepo, "worktree", "add", "-b", branch, worktree.toString()), this);
    }

    /**
     * Removes {@code worktree} from git's worktree metadata and deletes its directory.
     * If git itself refuses or fails to remove the worktree (e.g. a file locked by
     * another process), the directory is left in place instead of being force-deleted
     * out from under git -- deleting it anyway would leave {@code .git/worktrees} out
     * of sync with the filesystem, corrupting later {@code git worktree} operations.
     */
    void removeWorktree(Path targetRepo, Path worktree) {
        CommandResult removeResult = git(targetRepo, "worktree", "remove", "--force", worktree.toString());
        if (removeResult.exitCode() != 0) {
            LOG.warn("git worktree remove failed for {}; leaving it in place: {}",
                    worktree, commandFailureDetail(removeResult));
            return;
        }
        fileStore.deleteRecursively(worktree);
    }

    boolean hasChanges(Path worktree) {
        CommandResult status = git(worktree, "status", "--porcelain");
        requireGitSuccess("git status failed in worktree", GitOutcome.of(status, this));
        return !status.standardOutput().isBlank();
    }
    
    GitOutcome addAll(Path repository) {
        return GitOutcome.of(git(repository, "add", "-A"), this);
    }

    GitOutcome gitAddPaths(Path repository, Path... paths) {
        List<String> command = new ArrayList<>();
        command.add("add");
        command.add("--");
        for (Path path : paths) {
            if (path != null) {
                command.add(relativeName(repository, path));
            }
        }
        return GitOutcome.of(git(repository, command.toArray(String[]::new)), this);
    }

    GitOutcome commit(Path repository, String message) {
        return GitOutcome.of(git(repository, "commit", "-m", message), this);
    }

    GitOutcome push(Path repository) {
        return GitOutcome.of(git(repository, "push"), this);
    }

    GitOutcome pushUpstream(Path repository, String branch) {
        return GitOutcome.of(git(repository, "push", "-u", "origin", branch), this);
    }

    void requireGitSuccess(String operation, GitOutcome result) {
        if (!result.success()) {
            throw new GitCommandFailedException(operation + ": " + result.errorDetail());
        }
    }

    String commandFailureDetail(CommandResult result) {
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

    static String relativeName(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException notRelativizable) {
            return file.toString();
        }
    }

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


