package chatmap.infrastructure.importer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import chatmap.application.model.ImportedChat;
import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;

/** Parses the flat conversation format observed in Gemini Workspace Takeout. */
public final class GeminiConversationParser {
    private GeminiConversationParser() {
    }

    public static ImportedChat parse(JsonObject conversation, String externalId, String sourceUri, String importedAt) {
        if (!conversation.has("conversation_turns") || !conversation.get("conversation_turns").isJsonArray()) {
            throw new IllegalArgumentException("Expected conversation_turns array");
        }
        List<Message> drafts = new ArrayList<>();
        Set<Integer> indices = new HashSet<>();
        for (JsonElement element : conversation.getAsJsonArray("conversation_turns")) {
            JsonObject entry = object(element, "conversation turn");
            boolean user = entry.has("user_turn");
            if (entry.size() != 1 || (!user && !entry.has("system_turn"))) {
                throw new IllegalArgumentException("Expected exactly one user_turn or system_turn");
            }
            JsonObject turn = object(entry.get(user ? "user_turn" : "system_turn"), "turn body");
            int index = index(turn.get("turn_index"));
            if (!indices.add(index)) {
                throw new IllegalArgumentException("Duplicate turn_index " + index + "; ambiguous turn history");
            }
            String text = user ? string(turn.get("prompt"), "prompt") : responseText(turn);
            drafts.add(new Message(0, 0, user ? MessageRole.user : MessageRole.assistant, text, index,
                    timestamp(turn, "turn_last_modified"), entry.toString()));
        }
        drafts.sort(Comparator.comparingInt(Message::sequence));
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            Message draft = drafts.get(i);
            messages.add(new Message(0, 0, draft.role(), draft.text(), i, draft.timestamp(), draft.rawJson()));
        }
        String title = optionalString(conversation, "title");
        String updatedAt = timestamp(conversation, "last_modification_time");
        Chat chat = Chat.builder()
                .source(Source.geminiTakeoutJson)
                .title(title == null || title.isBlank() ? "Gemini conversation " + externalId : title)
                .createdAt(timestamp(conversation, "creation_time"))
                .updatedAt(updatedAt)
                .sourceUpdatedAt(updatedAt)
                .importedAt(importedAt)
                .lastImportedAt(importedAt)
                .externalConversationId(externalId)
                .sourceUri(sourceUri)
                .build();
        return new ImportedChat(chat, messages);
    }

    private static String responseText(JsonObject turn) {
        JsonElement text = turn.get("text");
        if (text == null || !text.isJsonArray()) {
            throw new IllegalArgumentException("Expected system_turn.text array");
        }
        List<String> parts = new ArrayList<>();
        for (JsonElement part : text.getAsJsonArray()) {
            parts.add(string(object(part, "text part").get("data"), "text part data"));
        }
        return String.join("\n\n", parts);
    }

    private static int index(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected numeric turn_index");
        }
        try {
            int index = element.getAsBigDecimal().intValueExact();
            if (index < 0) {
                throw new IllegalArgumentException("turn_index must be nonnegative");
            }
            return index;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("turn_index must be an integer within range", e);
        }
    }

    private static JsonObject object(JsonElement element, String name) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected object for " + name);
        }
        return element.getAsJsonObject();
    }

    private static String string(JsonElement element, String name) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string for " + name);
        }
        return element.getAsString();
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : string(element, key);
    }

    private static String timestamp(JsonObject object, String key) {
        String value = optionalString(object, key);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid timestamp for " + key, e);
        }
    }
}
