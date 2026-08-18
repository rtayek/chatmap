package chatmap.app;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import chatmap.application.port.ProjectRegistry;

import chatmap.application.port.llm.LlmProvider;
import chatmap.application.port.llm.ModelTarget;
import chatmap.application.port.llm.Channel;
import chatmap.application.port.handoff.HandoffFileStore;
import chatmap.application.service.HandoffOrchestratorService;
import chatmap.infrastructure.llm.DefaultLlmProviders;
import chatmap.infrastructure.command.ProcessRunner;
import chatmap.infrastructure.handoff.FileSystemHandoffFileStore;

/** Wires the infrastructure adapters into {@link HandoffOrchestratorService} for the CLI entry point. */
public final class HandoffOrchestratorBootstrap {

    /** Generous relative to a normal interactive prompt: an agent may be editing a real codebase unattended. */
    private static final Duration AGENT_TIMEOUT = Duration.ofMinutes(30);

    private HandoffOrchestratorBootstrap() {
    }

    public static HandoffOrchestratorService create(ProjectRegistry projectRegistry, Clock clock, boolean autoPush) {
        ProcessRunner commandExecutor = new ProcessRunner();
        Map<Channel, LlmProvider> providers = DefaultLlmProviders.providers(commandExecutor, AGENT_TIMEOUT);
        Map<String, ModelTarget> agentTargets = Map.of(
                "claude", ModelTarget.claude,
                "codex", ModelTarget.codex,
                "agy", ModelTarget.agy);
        HandoffFileStore fileStore = new FileSystemHandoffFileStore();
        return new HandoffOrchestratorService(commandExecutor, providers, agentTargets,
                fileStore, projectRegistry, clock, autoPush);
    }
}


