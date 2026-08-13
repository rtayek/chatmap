package chatmap.presentation.ui;

import chatmap.domain.ChatSummary;
import chatmap.domain.Message;
import chatmap.application.model.ChatExportModel;

/** Formats the selected chat detail panel. */
final class ChatDetailRenderer {
    private ChatDetailRenderer() {
    }

    static String render(ChatExportModel model, ChatSummary summary) {
        StringBuilder out = new StringBuilder();
        out.append(model.chat().title()).append("\n");
        out.append("Source: ").append(model.chat().source().displayName()).append("\n");
        out.append("Imported: ").append(model.chat().importedAt()).append("\n\n");
        if (summary != null) {
            out.append("AI Summary (").append(summary.generatedBy()).append("): ")
                    .append(summary.summary()).append("\n\n");
        }
        for (Message message : model.messages()) {
            out.append("[").append(message.role().displayName()).append("]\n");
            out.append(message.text()).append("\n\n");
        }
        return out.toString();
    }
}
