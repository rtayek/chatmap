package chatmap.application.service;

import java.util.Objects;

import chatmap.domain.PromptRouteRecord;

/** Result of classifying, routing, executing, and recording a prompt. */
public record PromptRoutingResult(
        ProjectContext projectContext,
        ConversationContext conversationContext,
        PromptClassification classification,
        ModelRoute route,
        PromptResult promptResult,
        PromptRouteRecord routeRecord) {

    public PromptRoutingResult {
        Objects.requireNonNull(projectContext, "projectContext");
        Objects.requireNonNull(conversationContext, "conversationContext");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(promptResult, "promptResult");
        Objects.requireNonNull(routeRecord, "routeRecord");
    }
}
