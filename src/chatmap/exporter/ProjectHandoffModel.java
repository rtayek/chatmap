package chatmap.exporter;

import java.util.List;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.Tag;

/** Fully hydrated project handoff model. */
public record ProjectHandoffModel(
        Project project,
        String exportedAt,
        List<ChatEntry> chats) {

    public ProjectHandoffModel {
        chats = List.copyOf(chats);
    }

    public record ChatEntry(
            Chat chat,
            List<Message> messages,
            List<Tag> tags) {

        public ChatEntry {
            messages = List.copyOf(messages);
            tags = List.copyOf(tags);
        }
    }
}
