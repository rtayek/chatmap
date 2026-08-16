package chatmap.infrastructure.importer;

import chatmap.application.model.ImportedChat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import chatmap.domain.Chat;
import chatmap.domain.ImportMetadata;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;

/** Shared parsing of a single ChatGPT conversation object. */
public final class ChatGptConversationParser {

    private ChatGptConversationParser() {
    }

    public static ImportedChat parse(JsonObject conversation, String defaultTitle, String sourceUri, String importedAt) {
        return parse(conversation, defaultTitle, sourceUri, importedAt, null);
    }

    public static ImportedChat parse(JsonObject conversation, String defaultTitle, String sourceUri, String importedAt, ChatGptImportCounter counter) {
        String externalId = firstString(conversation, "conversation_id", "id");
        JsonObject mapping = ChatGptMapping.object(conversation.get("mapping"));
        if (mapping == null || mapping.isEmpty()) {
            String fallbackId = externalId != null ? externalId : "missing-id";
            Chat emptyChat = Chat.builder()
                    .source(Source.chatgptJson)
                    .title(stringOr(conversation.get("title"), defaultTitle))
                    .importedAt(importedAt)
                    .externalConversationId(fallbackId)
                    .sourceUri(sourceUri)
                    .lastImportedAt(importedAt)
                    .build();
            return new ImportedChat(emptyChat, List.of());
        }

        String title = stringOr(conversation.get("title"), defaultTitle);
        String createdAt = ChatGptMapping.isoTimestamp(conversation.get("create_time"));
        String updatedAt = ChatGptMapping.isoTimestamp(conversation.get("update_time"));
        String currentNode = ChatGptMapping.string(conversation.get("current_node"));

        List<MessageDraft> drafts = orderedDrafts(mapping, currentNode, counter);
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            MessageDraft draft = drafts.get(i);
            messages.add(new Message(0, 0, draft.role(), draft.text(), i, draft.timestamp(), draft.rawJson()));
        }

        Chat chat = Chat.builder()
                .source(Source.chatgptJson)
                .title(title)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .importedAt(importedAt)
                .externalConversationId(externalId)
                .sourceUri(sourceUri)
                .sourceUpdatedAt(updatedAt)
                .lastImportedAt(importedAt)
                .build();
        return new ImportedChat(chat, messages);
    }

    private static List<MessageDraft> orderedDrafts(JsonObject mapping, String currentNode, ChatGptImportCounter counter) {
        if (currentNode != null && mapping.has(currentNode)) {
            return activePathDrafts(mapping, currentNode, counter);
        }
        boolean hasParentPointers = mapping.entrySet().stream()
                .map(e -> ChatGptMapping.object(e.getValue()))
                .filter(Objects::nonNull)
                .anyMatch(n -> n.has("parent") && !n.get("parent").isJsonNull());
        if (hasParentPointers) {
            String fallbackLeaf = fallbackLeaf(mapping);
            if (fallbackLeaf != null) {
                return activePathDrafts(mapping, fallbackLeaf, counter);
            }
        }
        return flatDraftsByTime(mapping, counter);
    }

    private static List<MessageDraft> activePathDrafts(JsonObject mapping, String startNodeId, ChatGptImportCounter counter) {
        List<MessageDraft> drafts = new ArrayList<>();
        int position = 0;
        for (JsonObject node : ChatGptMapping.activePath(mapping, startNodeId)) {
            MessageDraft draft = draftOf(ChatGptMapping.object(node.get("message")), position, counter);
            if (draft != null) {
                drafts.add(draft);
            }
            position++;
        }
        return drafts;
    }

    private static List<MessageDraft> flatDraftsByTime(JsonObject mapping, ChatGptImportCounter counter) {
        List<MessageDraft> drafts = new ArrayList<>();
        int position = 0;
        for (var entry : mapping.entrySet()) {
            JsonObject node = ChatGptMapping.object(entry.getValue());
            MessageDraft draft = node == null ? null : draftOf(ChatGptMapping.object(node.get("message")), position, counter);
            if (draft != null) {
                drafts.add(draft);
            }
            position++;
        }
        drafts.sort(Comparator
                .comparing((MessageDraft draft) -> draft.timestamp() == null)
                .thenComparing(draft -> draft.timestamp() == null ? "" : draft.timestamp())
                .thenComparingInt(MessageDraft::position));
        return drafts;
    }

    private static String fallbackLeaf(JsonObject mapping) {
        Set<String> parents = new HashSet<>();
        Map<String, JsonObject> nodes = new HashMap<>();
        for (var entry : mapping.entrySet()) {
            JsonObject node = ChatGptMapping.object(entry.getValue());
            if (node == null) {
                continue;
            }
            nodes.put(entry.getKey(), node);
            String parent = ChatGptMapping.string(node.get("parent"));
            if (parent != null) {
                parents.add(parent);
            }
        }
        return nodes.entrySet().stream()
                .filter(entry -> !parents.contains(entry.getKey()))
                .max(Comparator
                        .comparingDouble((Map.Entry<String, JsonObject> entry) -> messageTime(entry.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static double messageTime(JsonObject node) {
        JsonObject message = ChatGptMapping.object(node.get("message"));
        if (message == null || !message.has("create_time")) {
            return Double.NEGATIVE_INFINITY;
        }
        JsonElement value = message.get("create_time");
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble()
                : Double.NEGATIVE_INFINITY;
    }

    private static MessageDraft draftOf(JsonObject message, int position, ChatGptImportCounter counter) {
        if (message == null) {
            return null;
        }
        String text = flattenText(message, counter);
        if (text.isBlank()) {
            return null;
        }
        return new MessageDraft(
                ChatGptMapping.mapRole(message),
                text,
                ChatGptMapping.isoTimestamp(message.get("create_time")),
                message.toString(),
                position);
    }

    public static String flattenText(JsonObject message, ChatGptImportCounter counter) {
        JsonObject content = ChatGptMapping.object(message.get("content"));
        if (content == null) {
            if (counter != null) {
                counter.countUnsupported("missing_or_nonobject_content:" + typeName(message.get("content")));
            }
            return "";
        }
        List<String> parts = new ArrayList<>();
        String contentType = String.valueOf(content.get("content_type") == null
                || content.get("content_type").isJsonNull()
                        ? "unknown"
                        : content.get("content_type").getAsString());
        JsonArray partArray = array(content.get("parts"));
        if (partArray != null) {
            for (JsonElement part : partArray) {
                if (part == null || part.isJsonNull()) {
                    continue;
                }
                if (part.isJsonPrimitive() && part.getAsJsonPrimitive().isString()) {
                    addNonBlank(parts, part.getAsString());
                } else if (part.isJsonObject()) {
                    JsonObject partObject = part.getAsJsonObject();
                    String text = ChatGptMapping.string(partObject.get("text"));
                    if (text == null || text.isBlank()) {
                        if (counter != null) {
                            counter.countUnsupported("object_part:" + contentType + ":"
                                    + discriminator(partObject) + ":" + keySignature(partObject));
                        }
                    } else {
                        addNonBlank(parts, text);
                    }
                } else {
                    if (counter != null) {
                        counter.countUnsupported("non_string_part:" + contentType + ":" + typeName(part));
                    }
                }
            }
        } else {
            String text = ChatGptMapping.string(content.get("text"));
            if (text == null) {
                if (counter != null) {
                    counter.countUnsupported("missing_parts_and_text:" + contentType
                            + ":parts=" + typeName(content.get("parts"))
                            + ":text=" + typeName(content.get("text")));
                }
            } else {
                addNonBlank(parts, text);
            }
        }
        return String.join("\n\n", parts);
    }

    public static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = ChatGptMapping.string(object.get(key));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String stringOr(JsonElement element, String fallback) {
        String value = ChatGptMapping.string(element);
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static void addNonBlank(List<String> list, String text) {
        if (text != null && !text.isBlank()) {
            list.add(text);
        }
    }

    private static JsonArray array(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String typeName(JsonElement element) {
        if (element == null) return "null";
        if (element.isJsonNull()) return "json_null";
        if (element.isJsonObject()) return "object";
        if (element.isJsonArray()) return "array";
        if (element.isJsonPrimitive()) return "primitive";
        return "unknown";
    }

    private static String discriminator(JsonObject object) {
        for (String key : List.of("type", "content_type", "asset_pointer", "name", "kind")) {
            String value = ChatGptMapping.string(object.get(key));
            if (value != null && !value.isBlank()) {
                return key + "=" + value;
            }
        }
        return "none";
    }

    private static String keySignature(JsonObject object) {
        List<String> keys = new ArrayList<>(object.keySet());
        keys.sort(String::compareTo);
        return String.join("+", keys);
    }

    private record MessageDraft(MessageRole role, String text, String timestamp, String rawJson, int position) {
    }
}
