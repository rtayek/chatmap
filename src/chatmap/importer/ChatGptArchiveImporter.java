package chatmap.importer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import static chatmap.importer.ChatGptMapping.object;

import chatmap.domain.Chat;
import chatmap.domain.ImportMetadata;
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
            ChatGptImportCounter counter = new ChatGptImportCounter();
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
            JsonObject conversation, String importedAt, ChatGptImportCounter counter) {
        String externalId = ChatGptConversationParser.firstString(conversation, "conversation_id", "id");
        if (externalId == null || externalId.isBlank()) {
            return emptyConversation(entryName, index, "missing conversation id", importedAt);
        }
        JsonObject mapping = object(conversation.get("mapping"));
        if (mapping == null || mapping.isEmpty()) {
            return emptyConversation(entryName, index, externalId, importedAt);
        }

        String sourceUri = archive.toUri() + "!" + entryName + "#conversationId=" + externalId;
        ImportedChat imported = ChatGptConversationParser.parse(
                conversation, "Untitled ChatGPT conversation", sourceUri, importedAt, counter);

        return new ImportedConversation(entryName, externalId, imported);
    }

    private static ImportedConversation emptyConversation(String entryName, int index, String idOrReason,
            String importedAt) {
        String id = idOrReason.startsWith("missing ") ? entryName + "#" + index : idOrReason;
        Chat chat = new Chat(0, null, Source.chatgptJson, "Skipped ChatGPT conversation",
                null, null, importedAt, false, new ImportMetadata(id, null, null, null, importedAt));
        return new ImportedConversation(entryName, id, new ImportedChat(chat, List.of()));
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

    private static String concise(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
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
