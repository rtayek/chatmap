package chatmap.importer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;

/**
 * Imports ChatGPT {@code conversations.json}-style data: a bare conversation
 * object, or a top-level array of them. Conversation-tree traversal, role
 * mapping, and timestamps are shared with {@link ChatGptArchiveImporter} via
 * {@link ChatGptMapping}.
 */
public final class ChatGptJsonImporter {

    /**
     * Imports a single conversation (the object, or the first array element). Use
     * {@link #importAll} to import every conversation in an array export.
     */
    public ImportedChat importJson(String json, String importedAt) {
        List<ImportedChat> all = importAll(json, importedAt);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("expected ChatGPT conversation object or array");
        }
        return all.get(0);
    }

    /**
     * Imports every conversation: a bare object yields one chat, a top-level array
     * (a full {@code conversations.json} export) yields one chat per element, so
     * nothing is silently dropped.
     */
    public List<ImportedChat> importAll(String json, String importedAt) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(importedAt, "importedAt");

        JsonElement root = JsonParser.parseString(json);
        List<ImportedChat> imported = new ArrayList<>();
        for (JsonObject conversation : conversationObjects(root)) {
            imported.add(buildConversation(conversation, importedAt));
        }
        return imported;
    }

    private static List<JsonObject> conversationObjects(JsonElement root) {
        if (root.isJsonObject()) {
            return List.of(root.getAsJsonObject());
        }
        if (root.isJsonArray()) {
            List<JsonObject> objects = new ArrayList<>();
            for (JsonElement value : root.getAsJsonArray()) {
                if (value.isJsonObject()) {
                    objects.add(value.getAsJsonObject());
                }
            }
            return objects;
        }
        throw new IllegalArgumentException("expected ChatGPT conversation object or array");
    }

    private static ImportedChat buildConversation(JsonObject conversation, String importedAt) {
        String title = stringOr(conversation.get("title"), "ChatGPT Import");
        String createdAt = ChatGptMapping.isoTimestamp(conversation.get("create_time"));
        String updatedAt = ChatGptMapping.isoTimestamp(conversation.get("update_time"));
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
     * {@code current_node} the active branch is imported (edited-away replies
     * excluded); otherwise (hand-crafted JSON with no tree) every node is kept,
     * ordered by timestamp.
     */
    private static List<MessageDraft> orderedDrafts(JsonObject conversation) {
        JsonObject mapping = ChatGptMapping.object(conversation.get("mapping"));
        if (mapping == null) {
            return List.of();
        }
        String currentNode = ChatGptMapping.string(conversation.get("current_node"));
        if (currentNode != null && mapping.has(currentNode)) {
            return activePathDrafts(mapping, currentNode);
        }
        return flatDraftsByTime(mapping);
    }

    private static List<MessageDraft> activePathDrafts(JsonObject mapping, String currentNode) {
        List<MessageDraft> drafts = new ArrayList<>();
        int position = 0;
        for (JsonObject node : ChatGptMapping.activePath(mapping, currentNode)) {
            MessageDraft draft = draftOf(ChatGptMapping.object(node.get("message")), position);
            if (draft != null) {
                drafts.add(draft);
            }
            position++;
        }
        return drafts;
    }

    private static List<MessageDraft> flatDraftsByTime(JsonObject mapping) {
        List<MessageDraft> drafts = new ArrayList<>();
        int position = 0;
        for (var entry : mapping.entrySet()) {
            JsonObject node = ChatGptMapping.object(entry.getValue());
            MessageDraft draft = node == null ? null : draftOf(ChatGptMapping.object(node.get("message")), position);
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
                ChatGptMapping.mapRole(message),
                text,
                ChatGptMapping.isoTimestamp(message.get("create_time")),
                message.toString(),
                position);
    }

    private static String flattenText(JsonObject message) {
        JsonObject content = ChatGptMapping.object(message.get("content"));
        if (content == null) {
            return "";
        }
        JsonElement parts = content.get("parts");
        if (parts == null || !parts.isJsonArray()) {
            return stringOr(content.get("text"), "");
        }
        List<String> textParts = new ArrayList<>();
        for (JsonElement part : parts.getAsJsonArray()) {
            String value = ChatGptMapping.string(part);
            if (value != null && !value.isBlank()) {
                textParts.add(value);
            }
        }
        return String.join("\n\n", textParts);
    }

    private static String stringOr(JsonElement element, String fallback) {
        String value = ChatGptMapping.string(element);
        return value != null ? value : fallback;
    }

    private record MessageDraft(String role, String text, String timestamp, String rawJson, int position) {
    }
}
