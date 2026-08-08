package chatmap.importer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;

/** Minimal ChatGPT export importer for conversations.json style data. */
public final class ChatGptJsonImporter {

    /**
     * Imports a single conversation. A bare conversation object yields that one;
     * a top-level array yields its first element. Kept for callers that expect
     * exactly one chat; use {@link #importAll} to import every conversation in an
     * array export.
     */
    public ImportedChat importJson(String json, String importedAt) {
        List<ImportedChat> all = importAll(json, importedAt);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("expected ChatGPT conversation object or array");
        }
        return all.get(0);
    }

    /**
     * Imports every conversation in the JSON: a bare object yields one chat, a
     * top-level array (a full {@code conversations.json} export) yields one chat
     * per element, so nothing is silently dropped.
     */
    public List<ImportedChat> importAll(String json, String importedAt) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(importedAt, "importedAt");

        JsonValue root = new JsonParser(json).parse();
        List<ImportedChat> imported = new ArrayList<>();
        for (JsonObject conversation : conversationObjects(root)) {
            imported.add(buildConversation(conversation, importedAt));
        }
        return imported;
    }

    private static List<JsonObject> conversationObjects(JsonValue root) {
        if (root instanceof JsonObject object) {
            return List.of(object);
        }
        if (root instanceof JsonArray array) {
            List<JsonObject> objects = new ArrayList<>();
            for (JsonValue value : array.values) {
                if (value instanceof JsonObject object) {
                    objects.add(object);
                }
            }
            return objects;
        }
        throw new IllegalArgumentException("expected ChatGPT conversation object or array");
    }

    private static ImportedChat buildConversation(JsonObject conversation, String importedAt) {
        String title = stringValue(conversation.get("title"), "ChatGPT Import");
        String createdAt = isoTimestamp(conversation.get("create_time"));
        String updatedAt = isoTimestamp(conversation.get("update_time"));
        Chat chat = new Chat(0, null, Source.chatgptJson, title, createdAt, updatedAt, importedAt, false);

        List<MessageDraft> drafts = orderedDrafts(conversation);
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            MessageDraft draft = drafts.get(i);
            messages.add(new Message(0, 0, draft.role, draft.text, i, draft.timestamp, draft.rawJson));
        }

        return new ImportedChat(chat, messages);
    }

    /**
     * The conversation's messages in reading order. When the export names a
     * {@code current_node}, only its active branch is imported (root to that
     * node), so edited-away/regenerated replies are excluded, matching the ZIP
     * archive importer. Otherwise (hand-crafted JSON with no tree) every node is
     * kept, ordered by timestamp.
     */
    private static List<MessageDraft> orderedDrafts(JsonObject conversation) {
        JsonObject mapping = objectValue(conversation.get("mapping"));
        if (mapping == null) {
            return List.of();
        }
        String currentNode = stringValue(conversation.get("current_node"), null);
        if (currentNode != null && mapping.values.containsKey(currentNode)) {
            return activePathDrafts(mapping, currentNode);
        }
        return flatDraftsByTime(mapping);
    }

    /** Drafts along the active branch: current_node walked up its parent chain, root first. */
    private static List<MessageDraft> activePathDrafts(JsonObject mapping, String currentNode) {
        List<JsonObject> reversed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String nodeId = currentNode;
        while (nodeId != null && seen.add(nodeId)) {
            JsonObject node = objectValue(mapping.get(nodeId));
            if (node == null) {
                break;
            }
            reversed.add(node);
            nodeId = stringValue(node.get("parent"), null);
        }
        Collections.reverse(reversed);

        List<MessageDraft> drafts = new ArrayList<>();
        int position = 0;
        for (JsonObject node : reversed) {
            MessageDraft draft = draftOf(objectValue(node.get("message")), position);
            if (draft != null) {
                drafts.add(draft);
            }
            position++;
        }
        return drafts;
    }

    /** Every node with a message, ordered by timestamp (nulls last) then discovery order. */
    private static List<MessageDraft> flatDraftsByTime(JsonObject mapping) {
        List<MessageDraft> drafts = new ArrayList<>();
        int position = 0;
        for (JsonValue nodeValue : mapping.values.values()) {
            JsonObject node = objectValue(nodeValue);
            MessageDraft draft = node == null ? null : draftOf(objectValue(node.get("message")), position);
            if (draft != null) {
                drafts.add(draft);
            }
            position++;
        }
        drafts.sort(Comparator
                .comparing((MessageDraft draft) -> draft.timestamp == null)
                .thenComparing(draft -> draft.timestamp == null ? "" : draft.timestamp)
                .thenComparingInt(draft -> draft.position));
        return drafts;
    }

    /** A draft for a message node, or null when the node has no message or no text. */
    private static MessageDraft draftOf(JsonObject message, int position) {
        if (message == null) {
            return null;
        }
        String text = flattenText(message);
        if (text.isBlank()) {
            return null;
        }
        return new MessageDraft(
                mapRole(message),
                text,
                isoTimestamp(message.get("create_time")),
                message.raw,
                position);
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
