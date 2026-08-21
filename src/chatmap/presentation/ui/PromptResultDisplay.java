package chatmap.presentation.ui;

import java.util.Locale;
import java.util.stream.Collectors;

import chatmap.application.service.PromptRoutingResult;
import chatmap.domain.PromptClassificationReason;

/** Formats routed-prompt results for the JavaFX prompt pane. */
final class PromptResultDisplay {

    private PromptResultDisplay() {
    }

    static String classificationText(PromptRoutingResult result) {
        String reasons = result.classification().reasons().isEmpty()
                ? "no rule reason"
                : result.classification().reasons().stream()
                        .map(PromptClassificationReason::name)
                        .collect(Collectors.joining(", "));
        return "Classification: " + result.classification().level()
                + " (" + formatConfidence(result.classification().confidence()) + "; " + reasons + ")";
    }

    static String routeText(PromptRoutingResult result) {
        StringBuilder text = new StringBuilder("Route: ");
        text.append(result.route().target().channel().name())
                .append(" -> ")
                .append(result.route().target().displayName())
                .append(" [")
                .append(result.route().target().id())
                .append("]");
        result.promptResult().providerModelName()
                .ifPresent(model -> text.append(", model ").append(model));
        text.append(", provider ").append(result.promptResult().backendLabel());
        result.promptResult().sessionId()
                .ifPresent(session -> text.append(", session ").append(session));
        return text.toString();
    }

    static String successStatus(PromptRoutingResult result) {
        return "Prompt stored for " + result.projectContext().workingProjectIdentity()
                + " / " + result.conversationContext().id();
    }

    private static String formatConfidence(double confidence) {
        return String.format(Locale.ROOT, "%.2f", confidence);
    }
}
