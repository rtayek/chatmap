package chatmap.exporter;

import java.util.List;

import chatmap.domain.Chat;
import chatmap.domain.Message;

/** Fully hydrated single-chat export model. */
public record ChatExportModel(
        Chat chat,
        List<Message> messages) {

    public ChatExportModel {
        messages = List.copyOf(messages);
    }
}
