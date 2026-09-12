package chatmap.infrastructure.exporter;

import java.util.Objects;

import chatmap.application.model.ChatExportModel;
import chatmap.application.port.exporting.ChatExportFormatter;
import chatmap.domain.Chat;
import chatmap.domain.Message;

/** Deterministic Markdown export for one fully hydrated chat. */
public final class MarkdownExporter implements ChatExportFormatter {

    @Override
    public String format(ChatExportModel model) {
        return exportChat(model);
    }

    public String exportChat(ChatExportModel model) {
        Objects.requireNonNull(model, "model");

        Chat chat = model.chat();
        StringBuilder out = new StringBuilder();
        Markdown.heading(out, 1, chat.title());
        Markdown.sourceLine(out, chat);
        Markdown.metadata(out, "Created", chat.createdAt());
        Markdown.metadata(out, "Updated", chat.updatedAt());
        Markdown.metadata(out, "Imported", chat.importedAt());
        out.append("\n");

        for (int i = 0; i < model.messages().size(); i++) {
            Message message = model.messages().get(i);
            out.append("## ").append(message.role().dbValue()).append("\n\n");
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
}
