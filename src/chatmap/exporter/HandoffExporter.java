package chatmap.exporter;

import java.util.List;
import java.util.Objects;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.Tag;

/** Deterministic offline Markdown handoff for one project. */
public final class HandoffExporter {

    private static final int previewLimit = 160;

    public String exportProject(ProjectHandoffModel model) {
        Objects.requireNonNull(model, "model");

        StringBuilder out = new StringBuilder();
        Project project = model.project();
        out.append("# ").append(project.name()).append("\n\n");
        appendMetadata(out, "Exported", model.exportedAt());
        appendMetadata(out, "Description", project.description());
        out.append("Chats: ").append(model.chats().size()).append("\n\n");

        if (model.chats().isEmpty()) {
            out.append("_No chats._\n");
            return out.toString();
        }

        for (int i = 0; i < model.chats().size(); i++) {
            ProjectHandoffModel.ChatEntry entry = model.chats().get(i);
            appendChat(out, entry);
            if (i + 1 < model.chats().size()) {
                out.append("\n");
            }
        }

        return out.toString();
    }

    private static void appendChat(StringBuilder out, ProjectHandoffModel.ChatEntry entry) {
        Chat chat = entry.chat();
        out.append("## ").append(chat.title()).append("\n\n");
        out.append("Source: ").append(chat.source().dbValue()).append("\n");
        appendMetadata(out, "Imported", chat.importedAt());
        appendMetadata(out, "Created", chat.createdAt());
        appendMetadata(out, "Updated", chat.updatedAt());
        out.append("Archived: ").append(chat.archived() ? "yes" : "no").append("\n");
        out.append("Tags: ").append(formatTags(entry.tags())).append("\n");
        appendPreview(out, "First user", firstRole(entry.messages(), "user"));
        appendPreview(out, "Last assistant", lastRole(entry.messages(), "assistant"));
    }

    private static void appendMetadata(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank()) {
            out.append(label).append(": ").append(value).append("\n");
        }
    }

    private static void appendPreview(StringBuilder out, String label, Message message) {
        out.append(label).append(": ");
        if (message == null) {
            out.append("(none)\n");
        } else {
            out.append(preview(message.text())).append("\n");
        }
    }

    private static String formatTags(List<Tag> tags) {
        if (tags.isEmpty()) {
            return "(none)";
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(tags.get(i).name());
        }
        return out.toString();
    }

    private static Message firstRole(List<Message> messages, String role) {
        for (Message message : messages) {
            if (role.equals(message.role())) {
                return message;
            }
        }
        return null;
    }

    private static Message lastRole(List<Message> messages, String role) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (role.equals(message.role())) {
                return message;
            }
        }
        return null;
    }

    private static String preview(String text) {
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= previewLimit) {
            return normalized;
        }
        return normalized.substring(0, previewLimit - 3) + "...";
    }
}
