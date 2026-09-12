package chatmap.application.service;

import java.util.Objects;

/** Logical ChatMap conversation identity, independent of provider session ids. */
public record ConversationContext(String id) {
    public ConversationContext {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("conversation id must not be blank");
        }
        id = id.trim();
    }
}
