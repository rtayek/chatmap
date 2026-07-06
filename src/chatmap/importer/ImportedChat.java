package chatmap.importer;

import java.util.List;

import chatmap.domain.Chat;
import chatmap.domain.Message;

/** Normalized importer output before repository ids are assigned. */
public record ImportedChat(
        Chat chat,
        List<Message> messages) {

    public ImportedChat {
        messages = List.copyOf(messages);
    }
}
