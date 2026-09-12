package chatmap.presentation.ui;

import java.util.List;

import chatmap.domain.ChatSummary;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.application.model.ChatExportModel;

/** Formats the selected chat detail panel. */
final class ChatDetailRenderer {
    private ChatDetailRenderer() {
    }

    static String render(ChatExportModel model, ChatSummary summary, List<Project> relatedProjects) {
        StringBuilder out = new StringBuilder();
        out.append(model.chat().title()).append("\n");
        out.append("Source: ").append(model.chat().source().displayName()).append("\n");
        out.append("Imported: ").append(model.chat().importedAt()).append("\n\n");
        if (relatedProjects != null && !relatedProjects.isEmpty()) {
            out.append("Related Projects: ").append(formatProjects(relatedProjects)).append("\n\n");
        }
        if (summary != null) {
            out.append("LLM Summary (").append(summary.generatedBy()).append("): ")
                    .append(summary.summary()).append("\n\n");
        }
        for (Message message : model.messages()) {
            out.append("[").append(message.role().displayName()).append("]\n");
            out.append(message.text()).append("\n\n");
        }
        return out.toString();
    }

    private static String formatProjects(List<Project> projects) {
        return projects.stream()
                .map(Project::name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
