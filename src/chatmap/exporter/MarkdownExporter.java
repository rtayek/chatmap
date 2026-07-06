package chatmap.exporter;

import java.util.Objects;

import chatmap.domain.Chat;
import chatmap.domain.Message;

/** Deterministic Markdown export for one fully hydrated chat. */
public final class MarkdownExporter {

    public String exportChat(ChatExportModel model) {
        Objects.requireNonNull(model, "model");

        Chat chat = model.chat();
        StringBuilder out = new StringBuilder();
        out.append("# ").append(chat.title()).append("\n\n");
        out.append("Source: ").append(chat.source()).append("\n");
        appendMetadata(out, "Created", chat.createdAt());
        appendMetadata(out, "Updated", chat.updatedAt());
        appendMetadata(out, "Imported", chat.importedAt());
        out.append("\n");

        for (int i = 0; i < model.messages().size(); i++) {
            Message message = model.messages().get(i);
            out.append("## ").append(message.role()).append("\n\n");
            out.append(message.text());
            if (!message.text().endsWith("\n")) {
                out.append("\n");
            }
            if (i + 1 < model.messages().size()) {
                out.append("\n");
            }
        }

        return out.toString();
    }

    private static void appendMetadata(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank()) {
            out.append(label).append(": ").append(value).append("\n");
        }
    }
}
