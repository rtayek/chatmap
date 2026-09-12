package chatmap.infrastructure.provider;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Small JSONL reading + safe Gson accessors shared by the CLI-history parsers. */
public final class SessionLines {

    private SessionLines() {
    }

    /** All lines of a session file, or an empty list if it cannot be read. */
    public static List<String> read(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    /** Parses one line as a JSON object, or null when the line is blank or not an object. */
    public static JsonObject asObject(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(line);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    /** A string field, or null when absent/non-primitive. */
    public static String string(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return null;
        }
        JsonElement value = object.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
