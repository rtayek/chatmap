package chatmap.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        return ChatGptConversationParser.parse(conversation, "ChatGPT Import", null, importedAt);
    }
}
