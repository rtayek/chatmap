package chatmap.presentation.ui;

import java.util.Locale;
import java.util.stream.Collectors;

import chatmap.application.model.ChatExportModel;
import chatmap.application.service.PromptRoutingResult;
import chatmap.domain.Message;
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

    static String historyText(ChatExportModel model) {
        StringBuilder text = new StringBuilder();
        text.append(model.chat().title() == null || model.chat().title().isBlank()
                ? "Untitled chat" : model.chat().title()).append("\n");
        text.append("Source: ").append(model.chat().source().displayName()).append("\n");
        if (model.chat().providerSessionId() != null && !model.chat().providerSessionId().isBlank()) {
            text.append("Session: ").append(model.chat().providerSessionId()).append("\n");
        }
        text.append("\n");
        for (Message message : model.messages()) {
            text.append("[").append(message.role().displayName()).append("]\n");
            text.append(message.text() == null ? "" : message.text()).append("\n\n");
        }
        return text.toString();
    }

    private static String formatConfidence(double confidence) {
        return String.format(Locale.ROOT, "%.2f", confidence);
    }
}
