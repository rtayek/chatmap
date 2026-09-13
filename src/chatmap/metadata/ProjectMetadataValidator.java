package chatmap.metadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Deterministic, read-only validator for the .llm metadata pilot. It checks only
 * the rules already declared in .llm/manifest.json and never modifies the project.
 */
public final class ProjectMetadataValidator {

    public ProjectMetadataValidator(Path repoRoot) {
        this.repoRoot = repoRoot;
        this.manifestPath = repoRoot.resolve(".llm").resolve("manifest.json");
    }

    /** Runs every declared check and returns the collected failures. */
    public Result validate() throws IOException {
        List<String> failures = new ArrayList<>();
        if (!Files.isRegularFile(manifestPath)) {
            failures.add(manifestPath + ": manifest not found");
            return new Result(failures, 0, 0);
        }

        JsonObject manifest;
        try {
            manifest = JsonParser.parseString(Files.readString(manifestPath, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (RuntimeException exception) {
            failures.add(manifestPath + ": manifest is not valid JSON: " + exception.getMessage());
            return new Result(failures, 0, 0);
        }

        validateDeclaredPolicy(manifest, failures);

        String entrypoint = string(manifest, "entrypoint");
        List<String> requiredDocuments = strings(manifest, "required_documents");
        String handoffDirectory = string(manifest, "handoff_directory");

        JsonObject pilot = manifest.has("metadata_pilot")
                ? manifest.getAsJsonObject("metadata_pilot") : new JsonObject();
        List<String> pilotDocuments = strings(pilot, "documents");
        List<String> requiredFields = strings(pilot, "required_fields");
        Set<String> allowedLifecycle = new LinkedHashSet<>(strings(pilot, "allowed_lifecycle"));
        Set<String> allowedStatus = new LinkedHashSet<>(strings(pilot, "allowed_status"));

        checkExists(failures, entrypoint, "entrypoint");
        for (String document : requiredDocuments) {
            checkExists(failures, document, "required document");
        }
        if (handoffDirectory != null && !Files.isDirectory(repoRoot.resolve(handoffDirectory))) {
            failures.add(handoffDirectory + ": handoff directory does not exist");
        }

        if (entrypoint != null && !requiredDocuments.contains(entrypoint)) {
            failures.add(entrypoint + ": entrypoint is not listed in required_documents");
        }

        Map<String, List<String>> idToDocuments = new LinkedHashMap<>();
        for (String document : pilotDocuments) {
            if (!requiredDocuments.contains(document)) {
                failures.add(document + ": metadata-pilot document is not listed in required_documents");
            }
            validateDocument(failures, document, requiredFields, allowedLifecycle, allowedStatus, idToDocuments);
        }

        for (Map.Entry<String, List<String>> entry : idToDocuments.entrySet()) {
            if (entry.getValue().size() > 1) {
                failures.add("id '" + entry.getKey() + "' is duplicated across "
                        + String.join(", ", entry.getValue()));
            }
        }

        return new Result(failures, pilotDocuments.size(), requiredDocuments.size());
    }

    private static void validateDeclaredPolicy(JsonObject manifest, List<String> failures) {
        JsonElement element = manifest.get("validation");
        if (element == null || !element.isJsonObject()) {
            failures.add("manifest validation block is missing or is not an object");
            return;
        }

        JsonObject validation = element.getAsJsonObject();
        requireStringPolicy(validation, failures, "encoding", "UTF-8");
        requireBooleanPolicy(validation, failures, "bom", false);
        requireStringPolicy(validation, failures, "line_endings", "LF");
        requireBooleanPolicy(validation, failures, "require_unique_ids", true);
        requireBooleanPolicy(validation, failures, "require_paths_exist", true);
    }

    private static void requireStringPolicy(JsonObject validation, List<String> failures,
            String key, String expected) {
        JsonElement element = validation.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()
                || !expected.equals(element.getAsString())) {
            failures.add("validation." + key + " must be '" + expected + "'");
        }
    }

    private static void requireBooleanPolicy(JsonObject validation, List<String> failures,
            String key, boolean expected) {
        JsonElement element = validation.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()
                || element.getAsBoolean() != expected) {
            failures.add("validation." + key + " must be " + expected);
        }
    }

    private void validateDocument(List<String> failures, String document, List<String> requiredFields,
            Set<String> allowedLifecycle, Set<String> allowedStatus, Map<String, List<String>> idToDocuments)
            throws IOException {
        Path path = repoRoot.resolve(document);
        if (!Files.isRegularFile(path)) {
            return;
        }

        byte[] bytes = Files.readAllBytes(path);
        if (hasBom(bytes)) {
            failures.add(document + ": file has a UTF-8 BOM");
        }
        if (hasCarriageReturn(bytes)) {
            failures.add(document + ": file uses CRLF line endings, expected LF");
        }
        String text = decodeUtf8(bytes);
        if (text == null) {
            failures.add(document + ": file is not valid UTF-8");
            return;
        }
        if (!text.isEmpty() && text.charAt(0) == 0xFEFF) {
            text = text.substring(1);
        }

        Map<String, String> frontMatter = parseFrontMatter(failures, document, text);
        if (frontMatter == null) {
            return;
        }
        for (String field : requiredFields) {
            String value = frontMatter.get(field);
            if (value == null || value.isBlank()) {
                failures.add(document + ": front-matter field '" + field + "' is missing or blank");
            }
        }
        String lifecycle = frontMatter.get("lifecycle");
        if (lifecycle != null && !lifecycle.isBlank() && !allowedLifecycle.contains(lifecycle)) {
            failures.add(document + ": lifecycle '" + lifecycle + "' is not in the allowed list " + allowedLifecycle);
        }
        String status = frontMatter.get("status");
        if (status != null && !status.isBlank() && !allowedStatus.contains(status)) {
            failures.add(document + ": status '" + status + "' is not in the allowed list " + allowedStatus);
        }
        String id = frontMatter.get("id");
        if (id != null && !id.isBlank()) {
            idToDocuments.computeIfAbsent(id, key -> new ArrayList<>()).add(document);
        }
    }

    private Map<String, String> parseFrontMatter(List<String> failures, String document, String text) {
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !stripCr(lines[0]).equals("---")) {
            failures.add(document + ": does not begin with YAML front matter");
            return null;
        }
        int closing = -1;
        for (int i = 1; i < lines.length; i++) {
            if (stripCr(lines[i]).equals("---")) {
                closing = i;
                break;
            }
        }
        if (closing < 0) {
            failures.add(document + ": front matter has no closing '---' delimiter");
            return null;
        }

        Map<String, String> frontMatter = new LinkedHashMap<>();
        for (int i = 1; i < closing; i++) {
            String line = stripCr(lines[i]);
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                failures.add(document + ": front-matter line is not 'key: value': " + line);
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (frontMatter.containsKey(key)) {
                failures.add(document + ": duplicate front-matter key '" + key + "'");
                continue;
            }
            frontMatter.put(key, value);
        }
        return frontMatter;
    }

    private void checkExists(List<String> failures, String relativePath, String label) {
        if (relativePath == null) {
            failures.add("manifest is missing the " + label);
            return;
        }
        if (!Files.exists(repoRoot.resolve(relativePath))) {
            failures.add(relativePath + ": " + label + " does not exist");
        }
    }

    private static boolean hasBom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
    }

    private static boolean hasCarriageReturn(byte[] bytes) {
        for (byte b : bytes) {
            if (b == '\r') {
                return true;
            }
        }
        return false;
    }

    private static String decodeUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static List<String> strings(JsonObject object, String key) {
        List<String> values = new ArrayList<>();
        JsonElement element = object.get(key);
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                values.add(item.getAsString());
            }
        }
        return values;
    }

    /** Outcome of a validation run: an ordered list of failures plus summary counts. */
    public record Result(List<String> failures, int pilotDocumentCount, int requiredDocumentCount) {
        public boolean passed() {
            return failures.isEmpty();
        }
    }

    private final Path repoRoot;
    private final Path manifestPath;
}
