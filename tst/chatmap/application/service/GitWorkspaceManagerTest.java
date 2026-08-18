package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.infrastructure.handoff.FileSystemHandoffFileStore;

class GitWorkspaceManagerTest {

    private static final HandoffFileStore FILE_STORE = new FileSystemHandoffFileStore();

    @TempDir
    Path repo;

    @Test
    void removeWorktreeLeavesTheDirectoryInPlaceWhenGitRemovalFails() throws IOException {
        Path worktree = Files.createDirectory(repo.resolve("worktree"));
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git worktree remove",
                new CommandResult(1, "", "Permission denied", Duration.ofMillis(10), false));
        GitWorkspaceManager gitManager = new GitWorkspaceManager(executor, FILE_STORE);

        gitManager.removeWorktree(repo, worktree);

        assertTrue(Files.exists(worktree),
                "a failed 'git worktree remove' must not be followed by a force-delete -- "
                        + "that would leave .git/worktrees metadata pointing at a directory that no longer exists");
    }

    @Test
    void removeWorktreeDeletesTheDirectoryWhenGitRemovalSucceeds() throws IOException {
        Path worktree = Files.createDirectory(repo.resolve("worktree"));
        FakeCommandExecutor executor = new FakeCommandExecutor();
        executor.respond("git worktree remove", new CommandResult(0, "", "", Duration.ofMillis(10), false));
        GitWorkspaceManager gitManager = new GitWorkspaceManager(executor, FILE_STORE);

        gitManager.removeWorktree(repo, worktree);

        assertFalse(Files.exists(worktree), "a successful 'git worktree remove' should still clean up any stragglers");
    }

    /** Records every request and returns a scripted result for the first matching command-prefix key. */
    private static final class FakeCommandExecutor implements CommandExecutor {
        private final List<CommandRequest> calls = new ArrayList<>();
        private final Map<String, CommandResult> responses = new LinkedHashMap<>();

        void respond(String commandPrefix, CommandResult result) {
            responses.put(commandPrefix, result);
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
            return new CommandResult(0, "", "", Duration.ofMillis(10), false);
        }
    }
}
