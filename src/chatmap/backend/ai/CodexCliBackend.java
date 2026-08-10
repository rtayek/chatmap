package chatmap.backend.ai;

import chatmap.backend.command.CommandExecutor;

import chatmap.domain.Source;

import java.time.Duration;

public final class CodexCliBackend extends StandardCliBackend {
    static final BackendId BACKEND_ID = StandardCliBackend.CODEX_BACKEND_ID;

    public CodexCliBackend(CommandExecutor commandExecutor, Duration timeout) {
        super(BACKEND_ID, "codex", commandExecutor, timeout, Source.codexCliPrompt);
    }
}
