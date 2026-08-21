package chatmap.application.service;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import chatmap.application.port.persistence.PromptRouteStore;
import chatmap.domain.Project;
import chatmap.domain.PromptRouteRecord;

/** Orchestrates deterministic classification, target selection, provider execution, and persistence. */
public final class PromptRouterService {

    private final PromptClassifier classifier;
    private final PromptRouteSelector routeSelector;
    private final PromptService promptService;
    private final ProjectService projectService;
    private final PromptRouteStore promptRoutes;
    private final Clock clock;

    public PromptRouterService(
            PromptClassifier classifier,
            PromptRouteSelector routeSelector,
            PromptService promptService,
            ProjectService projectService,
            PromptRouteStore promptRoutes,
            Clock clock) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.routeSelector = Objects.requireNonNull(routeSelector, "routeSelector");
        this.promptService = Objects.requireNonNull(promptService, "promptService");
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.promptRoutes = Objects.requireNonNull(promptRoutes, "promptRoutes");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PromptRoutingResult route(ProjectContext projectContext, ConversationContext conversationContext,
            String prompt) throws SQLException {
        Objects.requireNonNull(projectContext, "projectContext");
        Objects.requireNonNull(conversationContext, "conversationContext");
        Objects.requireNonNull(prompt, "prompt");

        Instant started = clock.instant();
        Project project = projectService.findOrCreate(projectContext.workingProjectIdentity(),
                "ChatMap routed prompt project", started.toString());
        PromptClassification classification = classifier.classify(prompt);
        ModelRoute route = routeSelector.select(classification);

        PromptResult promptResult = promptService.submit(route.target().id(), prompt);
        projectService.assignChat(promptResult.chatId(), project.id());

        PromptRouteRecord saved = promptRoutes.insert(new PromptRouteRecord(
                0,
                promptResult.chatId(),
                projectContext.chatMapProjectIdentity(),
                projectContext.workingProjectIdentity(),
                conversationContext.id(),
                projectContext.repositoryPath().map(Path::toString),
                classification.level(),
                classification.confidence(),
                classification.reasons(),
                route.target().channel().name(),
                route.target().id(),
                promptResult.providerModelName(),
                promptResult.sessionId(),
                "SUCCEEDED",
                started.toString()));

        return new PromptRoutingResult(projectContext, conversationContext, classification, route, promptResult, saved);
    }
}
