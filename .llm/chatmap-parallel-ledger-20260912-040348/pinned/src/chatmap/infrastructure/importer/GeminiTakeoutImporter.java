package chatmap.infrastructure.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import chatmap.application.model.ImportedChat;
import chatmap.application.port.importing.ConversationFileReader;

/** Reads an extracted Takeout root; parsing all files precedes any persistence. */
public final class GeminiTakeoutImporter implements ConversationFileReader {
    @Override
    public List<ImportedChat> read(Path directory, String importedAt) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("Expected an extracted Takeout directory, not an archive: " + root);
        }
        Path history = root.resolve("Gemini in Workspace/Conversation History");
        if (!Files.isDirectory(history)) {
            throw new IOException("Missing Gemini in Workspace/Conversation History under " + root);
        }
        List<Path> files;
        try (var entries = Files.list(history)) {
            files = entries.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> fileNamePattern.matcher(history.relativize(path).toString()).matches())
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        if (files.isEmpty()) {
            throw new IOException("No conversation_<id>.txt files in " + history);
        }
        List<ImportedChat> imported = new ArrayList<>();
        for (Path file : files) {
            String name = history.relativize(file).toString();
            String id = name.substring("conversation_".length(), name.length() - ".txt".length());
            try (JsonReader json = new JsonReader(Files.newBufferedReader(file))) {
                json.setStrictness(Strictness.STRICT);
                var document = JsonParser.parseReader(json);
                if (!document.isJsonObject() || json.peek() != JsonToken.END_DOCUMENT) {
                    throw new IllegalArgumentException("Expected one conversation JSON object");
                }
                imported.add(GeminiConversationParser.parse(
                        document.getAsJsonObject(), id, file.toUri().toString(), importedAt));
            } catch (IOException | RuntimeException e) {
                throw new IOException("Could not read " + file + ": " + e.getMessage(), e);
            }
        }
        return List.copyOf(imported);
    }

    private static final Pattern fileNamePattern = Pattern.compile("conversation_([0-9]+)\\.txt");
}
