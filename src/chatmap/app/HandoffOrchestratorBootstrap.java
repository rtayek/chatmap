package chatmap.app;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import chatmap.application.port.ai.CommandBackedAiBackend;
import chatmap.application.service.HandoffOrchestratorService;
import chatmap.infrastructure.ai.AgyCliBackend;
import chatmap.infrastructure.ai.ClaudeCliBackend;
import chatmap.infrastructure.ai.CodexCliBackend;
import chatmap.infrastructure.command.CommandRunner;

/** Wires the infrastructure adapters into {@link HandoffOrchestratorService} for the CLI entry point. */
public final class HandoffOrchestratorBootstrap {

    /** Generous relative to a normal interactive prompt: an agent may be editing a real codebase unattended. */
    private static final Duration AGENT_TIMEOUT = Duration.ofMinutes(30);

    private HandoffOrchestratorBootstrap() {
    }

    public static HandoffOrchestratorService create(Map<String, Path> projectRegistry, Clock clock, boolean autoPush) {
        CommandRunner commandExecutor = new CommandRunner();
        Map<String, CommandBackedAiBackend> agentBackends = Map.of(
                "claude", new ClaudeCliBackend(commandExecutor, AGENT_TIMEOUT),
                "codex", new CodexCliBackend(commandExecutor, AGENT_TIMEOUT),
                "agy", new AgyCliBackend(commandExecutor, AGENT_TIMEOUT));
        return new HandoffOrchestratorService(commandExecutor, agentBackends, projectRegistry, clock, autoPush);
    }
}
