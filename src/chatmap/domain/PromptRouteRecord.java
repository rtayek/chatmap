package chatmap.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persisted metadata for one routed prompt turn. */
public record PromptRouteRecord(
        long id,
        long chatId,
        String chatMapProjectIdentity,
        long workingProjectId,
        String workingProjectIdentity,
        String conversationId,
        Optional<String> repositoryPath,
        PromptClassificationLevel classificationLevel,
        double classificationConfidence,
        List<PromptClassificationReason> classificationReasons,
        String routeProviderId,
        String routeModelTargetId,
        Optional<String> providerModelName,
        Optional<String> providerSessionId,
        String requestStatus,
        String createdAt) {

    public PromptRouteRecord {
        Objects.requireNonNull(chatMapProjectIdentity, "chatMapProjectIdentity");
        if (workingProjectId < 0) {
            throw new IllegalArgumentException("workingProjectId must not be negative");
        }
        Objects.requireNonNull(workingProjectIdentity, "workingProjectIdentity");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(repositoryPath, "repositoryPath");
        Objects.requireNonNull(classificationLevel, "classificationLevel");
        classificationReasons = List.copyOf(Objects.requireNonNull(classificationReasons, "classificationReasons"));
        Objects.requireNonNull(routeProviderId, "routeProviderId");
        Objects.requireNonNull(routeModelTargetId, "routeModelTargetId");
        Objects.requireNonNull(providerModelName, "providerModelName");
        Objects.requireNonNull(providerSessionId, "providerSessionId");
        Objects.requireNonNull(requestStatus, "requestStatus");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
