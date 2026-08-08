package chatmap.backend;

import java.time.Duration;

public final class ClaudeCliBackend extends StandardCliBackend {
    static final BackendId BACKEND_ID = StandardCliBackend.CLAUDE_BACKEND_ID;

    public ClaudeCliBackend() {
        this(new CommandRunner(), Duration.ofMinutes(3));
    }

    public ClaudeCliBackend(Duration timeout) {
        this(new CommandRunner(), timeout);
    }

    public ClaudeCliBackend(CommandExecutor commandExecutor, Duration timeout) {
        super(BACKEND_ID, "claude", commandExecutor, timeout);
    }
}
