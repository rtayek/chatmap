package chatmap.infrastructure.llm;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

final class StructuredCliOutput {
    private static final List<String> SESSION_FIELDS = List.of(
            "session_id", "sessionId", "conversation_id", "conversationId", "thread_id", "threadId");

    private StructuredCliOutput() {
    }

    static Parsed parse(String standardOutput, String fallbackSessionId) {
        List<String> texts = new ArrayList<>();
        String sessionId = blankToNull(fallbackSessionId);
        boolean sawJson = false;
        for (String line : standardOutput.lines().toList()) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("{")) {
                continue;
            }
            try {
                JsonObject object = JsonParser.parseString(trimmed).getAsJsonObject();
                sawJson = true;
                sessionId = firstNonblank(sessionId, extractSessionId(object));
                extractAssistantText(object, texts);
            } catch (IllegalStateException | JsonParseException malformedJsonLine) {
                continue;
            }
        }
        if (!texts.isEmpty()) {
            return new Parsed(String.join("\n", texts), sessionId);
        }
        return new Parsed(sawJson ? "" : standardOutput, sessionId);
    }

    private static String extractSessionId(JsonObject object) {
        for (String field : SESSION_FIELDS) {
            String value = stringField(object, field);
            if (value != null) {
                return value;
            }
        }
        String type = stringField(object, "type");
        if (type != null && type.toLowerCase(java.util.Locale.ROOT).contains("session")) {
            return stringField(object, "id");
        }
        return null;
    }

    private static void extractAssistantText(JsonObject object, List<String> texts) {
        String type = stringField(object, "type");
        boolean assistantEvent = type == null || type.contains("assistant") || type.contains("agent_message")
                || type.contains("item.completed") || type.contains("result") || type.contains("final")
                || type.contains("response");
        if (!assistantEvent) {
            return;
        }
        addStringField(object, "result", texts);
        addStringField(object, "response", texts);
        addStringField(object, "text", texts);
        addStringField(object, "content", texts);
        addStringField(object, "message", texts);
        if (object.has("message") && object.get("message").isJsonObject()) {
            extractAssistantText(object.getAsJsonObject("message"), texts);
        }
        if (object.has("item") && object.get("item").isJsonObject()) {
            extractAssistantText(object.getAsJsonObject("item"), texts);
        }
        if (object.has("content") && object.get("content").isJsonArray()) {
            extractContentArray(object.getAsJsonArray("content"), texts);
        }
    }

    private static void extractContentArray(JsonArray content, List<String> texts) {
        for (JsonElement item : content) {
            if (item.isJsonPrimitive()) {
                addText(item.getAsString(), texts);
            } else if (item.isJsonObject()) {
                addStringField(item.getAsJsonObject(), "text", texts);
            }
        }
    }

    private static void addStringField(JsonObject object, String field, List<String> texts) {
        addText(stringField(object, field), texts);
    }

    private static void addText(String value, List<String> texts) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            texts.add(normalized);
        }
    }

    private static String stringField(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull() || !object.get(field).isJsonPrimitive()) {
            return null;
        }
        return blankToNull(object.get(field).getAsString());
    }

    private static String firstNonblank(String current, String candidate) {
        return current == null ? blankToNull(candidate) : current;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    record Parsed(String text, String sessionId) {
    }
}
