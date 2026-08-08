package chatmap.importer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import static chatmap.importer.ChatGptMapping.isoTimestamp;
import static chatmap.importer.ChatGptMapping.mapRole;
import static chatmap.importer.ChatGptMapping.object;
import static chatmap.importer.ChatGptMapping.string;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;

/** Reads ChatGPT export ZIP conversation JSON without extracting the archive. */
public final class ChatGptArchiveImporter {

    public ArchiveReadResult read(Path zipPath, String importedAt) throws IOException {
        Path archive = zipPath.toAbsolutePath().normalize();
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            List<String> entries = conversationEntries(zip);
            if (entries.isEmpty()) {
                throw new IOException("No conversations.json or conversations-*.json entries found in "
                        + archive.getFileName());
            }

            List<ImportedConversation> conversations = new ArrayList<>();
            List<Failure> failures = new ArrayList<>();
            Counter counter = new Counter();
            int discovered = 0;
            int skipped = 0;
            for (String entryName : entries) {
                ZipEntry entry = zip.getEntry(entryName);
                try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8);
                        JsonReader json = new JsonReader(reader)) {
                    json.beginArray();
                    int index = 0;
                    while (json.hasNext()) {
                        discovered++;
                        try {
                            JsonObject conversation = JsonParser.parseReader(json).getAsJsonObject();
                            ImportedConversation imported = parseConversation(
                                    archive, entryName, index, conversation, importedAt, counter);
                            if (imported.importedChat().messages().isEmpty()) {
                                skipped++;
                            } else {
                                conversations.add(imported);
                            }
                        } catch (RuntimeException e) {
                            failures.add(new Failure(entryName, null,
                                    "conversation " + index + ": " + concise(e)));
                        }
                        index++;
                    }
                    json.endArray();
                } catch (RuntimeException e) {
                    failures.add(new Failure(entryName, null, "entry parse failed: " + concise(e)));
                }
            }
            return new ArchiveReadResult(archive, entries, discovered, conversations, skipped,
                    counter.unsupportedContentParts, counter.unsupportedContentCategories, failures);
        }
    }

    public CodexInspection inspectCodex(Path zipPath) throws IOException {
        Path archive = zipPath.toAbsolutePath().normalize();
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getEntry("codex.json");
            if (entry == null) {
                return new CodexInspection(false, "missing", 0, Set.of(), false, false);
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                String topLevel = root.isJsonArray() ? "array" : root.isJsonObject() ? "object" : "other";
                int count = root.isJsonArray() ? root.getAsJsonArray().size()
                        : root.isJsonObject() ? root.getAsJsonObject().size() : 0;
                Set<String> keys = sampleKeys(root);
                boolean normalSchema = keys.contains("mapping") && keys.contains("current_node");
                boolean codexTurns = keys.contains("id") && keys.contains("title") && keys.contains("turns");
                return new CodexInspection(true, topLevel, count, keys, normalSchema, codexTurns);
            }
        }
    }

    private static ImportedConversation parseConversation(Path archive, String entryName, int index,
            JsonObject conversation, String importedAt, Counter counter) {
        String externalId = firstString(conversation, "conversation_id", "id");
        if (externalId == null || externalId.isBlank()) {
            return emptyConversation(entryName, index, "missing conversation id", importedAt);
        }
        JsonObject mapping = object(conversation.get("mapping"));
        if (mapping == null || mapping.isEmpty()) {
            return emptyConversation(entryName, index, externalId, importedAt);
        }

        String currentNode = string(conversation.get("current_node"));
        List<JsonObject> activePath = activePath(mapping, currentNode);
        String title = nonBlank(string(conversation.get("title")), "Untitled ChatGPT conversation");
        String createdAt = isoTimestamp(conversation.get("create_time"));
        String updatedAt = isoTimestamp(conversation.get("update_time"));
        String sourceUri = archive.toUri() + "!" + entryName + "#conversationId=" + externalId;

        List<Message> messages = new ArrayList<>();
        int sequence = 0;
        for (JsonObject node : activePath) {
            JsonObject message = object(node.get("message"));
            if (message == null) {
                continue;
            }
            String text = messageText(message, counter);
            if (text.isBlank()) {
                continue;
            }
            messages.add(new Message(0, 0, mapRole(message), text, sequence++,
                    isoTimestamp(message.get("create_time")), null));
        }
        Chat chat = new Chat(0, null, Source.chatgptJson, title, createdAt, updatedAt,
                importedAt, false, externalId, sourceUri, null, updatedAt, importedAt);
        return new ImportedConversation(entryName, externalId, new ImportedChat(chat, messages));
    }

    private static ImportedConversation emptyConversation(String entryName, int index, String idOrReason,
            String importedAt) {
        String id = idOrReason.startsWith("missing ") ? entryName + "#" + index : idOrReason;
        Chat chat = new Chat(0, null, Source.chatgptJson, "Skipped ChatGPT conversation",
                null, null, importedAt, false, id, null, null, null, importedAt);
        return new ImportedConversation(entryName, id, new ImportedChat(chat, List.of()));
    }

    private static List<JsonObject> activePath(JsonObject mapping, String currentNode) {
        String start = currentNode != null && mapping.has(currentNode) ? currentNode : fallbackLeaf(mapping);
        return ChatGptMapping.activePath(mapping, start);
    }

    private static String fallbackLeaf(JsonObject mapping) {
        Set<String> parents = new HashSet<>();
        Map<String, JsonObject> nodes = new HashMap<>();
        for (var entry : mapping.entrySet()) {
            JsonObject node = object(entry.getValue());
            if (node == null) {
                continue;
            }
            nodes.put(entry.getKey(), node);
            String parent = string(node.get("parent"));
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
        JsonObject message = object(node.get("message"));
        if (message == null || !message.has("create_time")) {
            return Double.NEGATIVE_INFINITY;
        }
        JsonElement value = message.get("create_time");
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble()
                : Double.NEGATIVE_INFINITY;
    }

    private static String messageText(JsonObject message, Counter counter) {
        JsonObject content = object(message.get("content"));
        if (content == null) {
            counter.countUnsupported("missing_or_nonobject_content:" + typeName(message.get("content")));
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
                    String text = string(partObject.get("text"));
                    if (text == null || text.isBlank()) {
                        counter.countUnsupported("object_part:" + contentType + ":"
                                + discriminator(partObject) + ":" + keySignature(partObject));
                    } else {
                        addNonBlank(parts, text);
                    }
                } else {
                    counter.countUnsupported("non_string_part:" + contentType + ":" + typeName(part));
                }
            }
        } else {
            String text = string(content.get("text"));
            if (text == null) {
                counter.countUnsupported("missing_parts_and_text:" + contentType
                        + ":parts=" + typeName(content.get("parts"))
                        + ":text=" + typeName(content.get("text")));
            } else {
                addNonBlank(parts, text);
            }
        }
        return String.join("\n\n", parts);
    }

    private static List<String> conversationEntries(ZipFile zip) {
        TreeSet<String> numbered = new TreeSet<>();
        boolean legacy = false;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if ("conversations.json".equals(name)) {
                legacy = true;
            } else if (name.matches("conversations-\\d+\\.json")) {
                numbered.add(name);
            }
        }
        List<String> out = new ArrayList<>();
        if (legacy) {
            out.add("conversations.json");
        }
        out.addAll(numbered);
        return out;
    }

    private static Set<String> sampleKeys(JsonElement root) {
        JsonObject sample = null;
        if (root.isJsonObject()) {
            sample = root.getAsJsonObject();
        } else if (root.isJsonArray() && !root.getAsJsonArray().isEmpty()
                && root.getAsJsonArray().get(0).isJsonObject()) {
            sample = root.getAsJsonArray().get(0).getAsJsonObject();
        }
        if (sample == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(sample.keySet());
    }

    private static String firstString(JsonObject object, String... names) {
        for (String name : names) {
            String value = string(object.get(name));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static JsonArray array(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String discriminator(JsonObject object) {
        for (String key : List.of("type", "content_type", "asset_pointer", "mime_type")) {
            String value = string(object.get(key));
            if (value != null && !value.isBlank()) {
                return value.startsWith("file-") ? "asset_pointer:file-*" : value;
            }
        }
        return "no-discriminator";
    }

    private static String keySignature(JsonObject object) {
        return String.join(",", new TreeSet<>(object.keySet()));
    }

    private static String typeName(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonArray()) {
            return "array";
        }
        if (element.isJsonObject()) {
            return "object";
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                return "string";
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return "number";
            }
            if (element.getAsJsonPrimitive().isBoolean()) {
                return "boolean";
            }
        }
        return "unknown";
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void addNonBlank(List<String> parts, String text) {
        if (text != null && !text.isBlank()) {
            parts.add(text.strip());
        }
    }

    private static String concise(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private static final class Counter {
        int unsupportedContentParts;

        final Map<String, Integer> unsupportedContentCategories = new LinkedHashMap<>();

        void countUnsupported(String category) {
            unsupportedContentParts++;
            unsupportedContentCategories.merge(category, 1, Integer::sum);
        }
    }

    public record ImportedConversation(String entryName, String externalConversationId,
            ImportedChat importedChat) {
    }

    public record Failure(String entryName, String conversationId, String reason) {
    }

    public record ArchiveReadResult(
            Path archivePath,
            List<String> conversationEntries,
            int conversationsDiscovered,
            List<ImportedConversation> conversations,
            int skipped,
            int unsupportedContentParts,
            Map<String, Integer> unsupportedContentCategories,
            List<Failure> failures) {
        public ArchiveReadResult {
            conversationEntries = List.copyOf(conversationEntries);
            conversations = List.copyOf(conversations);
            unsupportedContentCategories = Map.copyOf(unsupportedContentCategories);
            failures = List.copyOf(failures);
        }
    }

    public record CodexInspection(
            boolean present,
            String topLevelType,
            int recordCount,
            Set<String> sampleKeys,
            boolean normalConversationSchema,
            boolean codexTurnSchema) {
        public CodexInspection {
            sampleKeys = Set.copyOf(sampleKeys);
        }
    }
}
