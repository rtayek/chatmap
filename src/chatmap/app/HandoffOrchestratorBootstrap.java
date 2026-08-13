package chatmap.app;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

import chatmap.application.service.HandoffOrchestratorService;
import chatmap.infrastructure.command.CommandRunner;

/** Wires the infrastructure adapter into {@link HandoffOrchestratorService} for the CLI entry point. */
public final class HandoffOrchestratorBootstrap {

    private HandoffOrchestratorBootstrap() {
    }

    public static HandoffOrchestratorService create(Map<String, Path> projectRegistry, Clock clock, boolean autoPush) {
        return new HandoffOrchestratorService(new CommandRunner(), projectRegistry, clock, autoPush);
    }
}
