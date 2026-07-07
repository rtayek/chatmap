package chatmap.importer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;

/** Minimal ChatGPT export importer for conversations.json style data. */
public final class ChatGptJsonImporter {

    public ImportedChat importJson(String json, String importedAt) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(importedAt, "importedAt");

        JsonValue root = new JsonParser(json).parse();
        JsonObject conversation = firstConversation(root);
        String title = stringValue(conversation.get("title"), "ChatGPT Import");
        String createdAt = isoTimestamp(conversation.get("create_time"));
        String updatedAt = isoTimestamp(conversation.get("update_time"));
        Chat chat = new Chat(0, null, Source.chatgptJson, title, createdAt, updatedAt, importedAt, false);

        List<MessageDraft> drafts = messageDrafts(conversation);
        drafts.sort(Comparator
                .comparing((MessageDraft draft) -> draft.timestamp == null)
                .thenComparing(draft -> draft.timestamp == null ? "" : draft.timestamp)
                .thenComparingInt(draft -> draft.position));

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            MessageDraft draft = drafts.get(i);
            messages.add(new Message(0, 0, draft.role, draft.text, i, draft.timestamp, draft.rawJson));
        }

        return new ImportedChat(chat, messages);
    }

    private static JsonObject firstConversation(JsonValue root) {
        if (root instanceof JsonObject object) {
            return object;
        }
        if (root instanceof JsonArray array && !array.values.isEmpty() && array.values.get(0) instanceof JsonObject object) {
            return object;
        }
        throw new IllegalArgumentException("expected ChatGPT conversation object or array");
    }

    private static List<MessageDraft> messageDrafts(JsonObject conversation) {
        JsonObject mapping = objectValue(conversation.get("mapping"));
        if (mapping == null) {
            return List.of();
        }

        List<MessageDraft> drafts = new ArrayList<>();
        int position = 0;
        for (JsonValue nodeValue : mapping.values.values()) {
            JsonObject node = objectValue(nodeValue);
            JsonObject message = node == null ? null : objectValue(node.get("message"));
            if (message == null) {
                position++;
                continue;
            }

            String text = flattenText(message);
            if (text.isBlank()) {
                position++;
                continue;
            }

            drafts.add(new MessageDraft(
                    mapRole(message),
                    text,
                    isoTimestamp(message.get("create_time")),
                    message.raw,
                    position));
            position++;
        }
        return drafts;
    }

    private static String mapRole(JsonObject message) {
        JsonObject author = objectValue(message.get("author"));
        String role = author == null ? null : stringValue(author.get("role"), null);
        if ("user".equals(role) || "assistant".equals(role) || "system".equals(role)) {
            return role;
        }
        return "unknown";
    }

    private static String flattenText(JsonObject message) {
        JsonObject content = objectValue(message.get("content"));
        if (content == null) {
            return "";
        }

        JsonArray parts = arrayValue(content.get("parts"));
        if (parts == null) {
            return stringValue(content.get("text"), "");
        }

        List<String> textParts = new ArrayList<>();
        for (JsonValue part : parts.values) {
            if (part instanceof JsonString string && !string.value.isBlank()) {
                textParts.add(string.value);
            }
        }
        return String.join("\n\n", textParts);
    }

    private static String isoTimestamp(JsonValue value) {
        if (value instanceof JsonNumber number) {
            long seconds = (long) number.value;
            long nanos = Math.round((number.value - seconds) * 1_000_000_000L);
            return Instant.ofEpochSecond(seconds, nanos).toString();
        }
        return null;
    }

    private static JsonObject objectValue(JsonValue value) {
        return value instanceof JsonObject object ? object : null;
    }

    private static JsonArray arrayValue(JsonValue value) {
        return value instanceof JsonArray array ? array : null;
    }

    private static String stringValue(JsonValue value, String fallback) {
        return value instanceof JsonString string ? string.value : fallback;
    }

    private record MessageDraft(String role, String text, String timestamp, String rawJson, int position) {
    }

    private sealed interface JsonValue permits JsonObject, JsonArray, JsonString, JsonNumber, JsonLiteral {
    }

    private record JsonObject(Map<String, JsonValue> values, String raw) implements JsonValue {
        JsonValue get(String key) {
            return values.get(key);
        }
    }

    private record JsonArray(List<JsonValue> values, String raw) implements JsonValue {
    }

    private record JsonString(String value, String raw) implements JsonValue {
    }

    private record JsonNumber(double value, String raw) implements JsonValue {
    }

    private record JsonLiteral(Object value, String raw) implements JsonValue {
    }

    private static final class JsonParser {

        private final String text;
        private int index;

        JsonParser(String text) {
            this.text = text;
        }

        JsonValue parse() {
            JsonValue value = parseValue();
            skipWhitespace();
            if (index != text.length()) {
                throw error("unexpected trailing content");
            }
            return value;
        }

        private JsonValue parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("unexpected end of JSON");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private JsonObject parseObject() {
            int start = index;
            expect('{');
            Map<String, JsonValue> values = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return new JsonObject(values, text.substring(start, index));
            }
            while (true) {
                JsonString key = parseString();
                skipWhitespace();
                expect(':');
                values.put(key.value, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return new JsonObject(values, text.substring(start, index));
                }
                expect(',');
            }
        }

        private JsonArray parseArray() {
            int start = index;
            expect('[');
            List<JsonValue> values = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return new JsonArray(values, text.substring(start, index));
            }
            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return new JsonArray(values, text.substring(start, index));
                }
                expect(',');
            }
        }

        private JsonString parseString() {
            int start = index;
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return new JsonString(out.toString(), text.substring(start, index));
                }
                if (c == '\\') {
                    out.append(parseEscape());
                } else {
                    out.append(c);
                }
            }
            throw error("unterminated string");
        }

        private char parseEscape() {
            if (index >= text.length()) {
                throw error("unterminated escape");
            }
            char c = text.charAt(index++);
            return switch (c) {
                case '"', '\\', '/' -> c;
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> parseUnicode();
                default -> throw error("invalid escape");
            };
        }

        private char parseUnicode() {
            if (index + 4 > text.length()) {
                throw error("invalid unicode escape");
            }
            int value = Integer.parseInt(text.substring(index, index + 4), 16);
            index += 4;
            return (char) value;
        }

        private JsonNumber parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (peek('.')) {
                index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (start == index) {
                throw error("expected JSON value");
            }
            String raw = text.substring(start, index);
            return new JsonNumber(Double.parseDouble(raw), raw);
        }

        private JsonLiteral parseLiteral(String literal, Object value) {
            int start = index;
            if (!text.startsWith(literal, index)) {
                throw error("invalid literal");
            }
            index += literal.length();
            return new JsonLiteral(value, text.substring(start, index));
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw error("expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at index " + index);
        }
    }
}
