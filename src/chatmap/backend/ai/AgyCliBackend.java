package chatmap.backend;

import java.time.Duration;

public final class AgyCliBackend extends StandardCliBackend {
    static final BackendId BACKEND_ID = StandardCliBackend.AGY_BACKEND_ID;

    public AgyCliBackend(CommandExecutor commandExecutor, Duration timeout) {
        super(BACKEND_ID, "agy", commandExecutor, timeout);
    }
}
