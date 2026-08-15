package chatmap.app;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import chatmap.application.port.ai.CommandBackedAiBackend;
import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.application.service.HandoffOrchestratorService;
import chatmap.infrastructure.ai.StandardCliBackend;
import chatmap.infrastructure.command.CommandRunner;
import chatmap.infrastructure.handoff.FileSystemHandoffFileStore;

/** Wires the infrastructure adapters into {@link HandoffOrchestratorService} for the CLI entry point. */
public final class HandoffOrchestratorBootstrap {

    /** Generous relative to a normal interactive prompt: an agent may be editing a real codebase unattended. */
    private static final Duration AGENT_TIMEOUT = Duration.ofMinutes(30);

    private HandoffOrchestratorBootstrap() {
    }

    public static HandoffOrchestratorService create(Map<String, Path> projectRegistry, Clock clock, boolean autoPush) {
        CommandRunner commandExecutor = new CommandRunner();
        Map<String, CommandBackedAiBackend> agentBackends = Map.of(
                "claude", StandardCliBackend.claude(commandExecutor, AGENT_TIMEOUT),
                "codex", StandardCliBackend.codex(commandExecutor, AGENT_TIMEOUT),
                "agy", StandardCliBackend.agy(commandExecutor, AGENT_TIMEOUT));
        HandoffFileStore fileStore = new FileSystemHandoffFileStore();
        return new HandoffOrchestratorService(commandExecutor, agentBackends, fileStore, projectRegistry, clock, autoPush);
    }
}
