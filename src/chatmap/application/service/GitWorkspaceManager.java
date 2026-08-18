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

    CommandResult pull(Path repository) {
        return git(repository, "pull");
    }

    CommandResult addWorktree(Path targetRepo, Path worktree, String branch) {
        CommandResult branchExists = git(targetRepo, "show-ref", "--verify", "--quiet", "refs/heads/" + branch);
        if (branchExists.exitCode() == 0) {
            return git(targetRepo, "worktree", "add", worktree.toString(), branch);
        }
        if (branchExists.exitCode() != 1) {
            throw new GitCommandFailedException("git show-ref failed: " + commandFailureDetail(branchExists));
        }
        return git(targetRepo, "worktree", "add", "-b", branch, worktree.toString());
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
        requireGitSuccess("git status failed in worktree", status);
        return !status.standardOutput().isBlank();
    }
    
    CommandResult addAll(Path repository) {
        return git(repository, "add", "-A");
    }

    CommandResult gitAddPaths(Path repository, Path... paths) {
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

    CommandResult commit(Path repository, String message) {
        return git(repository, "commit", "-m", message);
    }

    CommandResult push(Path repository) {
        return git(repository, "push");
    }

    CommandResult pushUpstream(Path repository, String branch) {
        return git(repository, "push", "-u", "origin", branch);
    }

    void requireGitSuccess(String operation, CommandResult result) {
        if (result.exitCode() != 0) {
            throw new GitCommandFailedException(operation + ": " + commandFailureDetail(result));
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
