package chatmap.backend;

import java.time.Duration;

public final class CodexCliBackend extends StandardCliBackend {
    static final BackendId BACKEND_ID = StandardCliBackend.CODEX_BACKEND_ID;

    public CodexCliBackend(CommandExecutor commandExecutor, Duration timeout) {
        super(BACKEND_ID, "codex", commandExecutor, timeout);
    }
}
