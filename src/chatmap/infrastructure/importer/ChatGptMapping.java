package chatmap.infrastructure.importer;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import chatmap.domain.MessageRole;

/**
 * Shared reading of a ChatGPT conversation {@code mapping} tree, used by both the
 * loose-{@code .json} importer and the export-ZIP importer so the format's quirks
 * live in one place.
 */
final class ChatGptMapping {

    private ChatGptMapping() {
    }

    /**
     * The message nodes from {@code startNodeId} up its {@code parent} chain, root
     * first. This is the active branch of the conversation, so edited-away and
     * regenerated replies (other branches) are excluded.
     */
    static List<JsonObject> activePath(JsonObject mapping, String startNodeId) {
        List<JsonObject> reversed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String nodeId = startNodeId;
        while (nodeId != null && seen.add(nodeId)) {
            JsonObject node = object(mapping.get(nodeId));
            if (node == null) {
                break;
            }
            reversed.add(node);
            nodeId = string(node.get("parent"));
        }
        Collections.reverse(reversed);
        return reversed;
    }

    /** The message's author role, normalized to the closed domain role set. */
    static MessageRole mapRole(JsonObject message) {
        JsonObject author = object(message.get("author"));
        String role = author == null ? null : string(author.get("role"));
        return MessageRole.fromDbValue(role);
    }

    /** A ChatGPT epoch-seconds {@code create_time} as a UTC ISO-8601 string, or null. */
    static String isoTimestamp(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double secondsValue = value.getAsDouble();
        long seconds = (long) secondsValue;
        long nanos = Math.round((secondsValue - seconds) * 1_000_000_000L);
        return Instant.ofEpochSecond(seconds, nanos).toString();
    }

    static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    static String string(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : null;
    }
}
