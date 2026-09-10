package chatmap.infrastructure.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;

class GeminiTakeoutImporterTest {
    @Test
    void importsCompleteSanitizedSampleWithMetadataAndCitations() throws Exception {
        Path file = GeminiTakeoutFixture.write(tempDir, "1786837272");
        var imported = reader.read(tempDir, importedAt).getFirst();

        assertEquals(Source.geminiTakeoutJson, imported.chat().source());
        assertEquals("1786837272", imported.chat().externalConversationId());
        assertEquals("Sanitized Gemini sample", imported.chat().title());
        assertEquals("2026-08-15T23:41:12.049807Z", imported.chat().createdAt());
        assertEquals("2026-08-15T23:51:44.213743Z", imported.chat().sourceUpdatedAt());
        assertEquals(importedAt, imported.chat().lastImportedAt());
        assertEquals(file.toUri().toString(), imported.chat().sourceUri());
        assertEquals(16, imported.messages().size());
        for (int i = 0; i < 16; i++) {
            Message message = imported.messages().get(i);
            assertEquals(i, message.sequence());
            assertEquals(i % 2 == 0 ? MessageRole.user : MessageRole.assistant, message.role());
        }
        assertTrue(imported.messages().get(3).rawJson().contains("citations"));
        assertTrue(imported.messages().get(3).rawJson().contains("https://example.com/reference/1"));
    }

    @Test
    void importsMultipleFilesDeterministicallyAndJoinsAllTextParts() throws Exception {
        GeminiTakeoutFixture.write(tempDir, "1786837273");
        GeminiTakeoutFixture.write(tempDir, "1786837272");
        Files.writeString(tempDir.resolve("unrelated.txt"), "not JSON");

        var imported = reader.read(tempDir, importedAt);

        assertEquals(List.of("1786837272", "1786837273"),
                imported.stream().map(chat -> chat.chat().externalConversationId()).toList());
        assertEquals("First paragraph.\n\nSecond paragraph.", imported.get(1).messages().getLast().text());
    }

    @Test
    void sortsNonContiguousIndicesWithoutRequiringAlternation() throws Exception {
        JsonObject object = sample();
        var turns = object.getAsJsonArray("conversation_turns");
        turns.get(0).getAsJsonObject().getAsJsonObject("user_turn").addProperty("turn_index", 9);
        turns.add(turns.get(0).deepCopy());
        turns.get(2).getAsJsonObject().getAsJsonObject("user_turn").addProperty("turn_index", 7);
        var imported = parse(object);
        assertEquals(List.of(MessageRole.assistant, MessageRole.user, MessageRole.user),
                imported.messages().stream().map(Message::role).toList());
        assertEquals(List.of(0, 1, 2), imported.messages().stream().map(Message::sequence).toList());
    }

    @Test
    void rejectsDuplicateNegativeAndFractionalIndices() throws Exception {
        for (Number index : List.of(1, -1, 0.5)) {
            JsonObject object = sample();
            object.getAsJsonArray("conversation_turns").get(0).getAsJsonObject()
                    .getAsJsonObject("user_turn").addProperty("turn_index", index);
            assertThrows(IllegalArgumentException.class, () -> parse(object));
        }
    }

    @Test
    void rejectsUnsupportedTurnAndTextShapesAndBadTimestamps() throws Exception {
        JsonObject unknown = sample();
        unknown.getAsJsonArray("conversation_turns").get(0).getAsJsonObject().add("tool_turn", new JsonObject());
        assertThrows(IllegalArgumentException.class, () -> parse(unknown));

        JsonObject unsupportedText = sample();
        unsupportedText.getAsJsonArray("conversation_turns").get(1).getAsJsonObject()
                .getAsJsonObject("system_turn").getAsJsonArray("text").get(0).getAsJsonObject().remove("data");
        assertThrows(IllegalArgumentException.class, () -> parse(unsupportedText));

        JsonObject badTime = sample();
        badTime.addProperty("creation_time", "yesterday");
        assertThrows(IllegalArgumentException.class, () -> parse(badTime));
    }

    @Test
    void requiresExtractedTakeoutDirectoryAndConversationFiles() throws Exception {
        assertThrows(IOException.class, () -> reader.read(tempDir, importedAt));
        Path archive = Files.writeString(tempDir.resolve("takeout.tgz"), "not a directory");
        assertThrows(IOException.class, () -> reader.read(archive, importedAt));
    }

    @Test
    void rejectsMalformedOrTrailingJsonWithFileContext() throws Exception {
        Path file = GeminiTakeoutFixture.write(tempDir, "1786837272");
        for (String text : List.of("{", "{}", GeminiTakeoutFixture.json("1786837272") + " {}")) {
            Files.writeString(file, text);
            IOException error = assertThrows(IOException.class, () -> reader.read(tempDir, importedAt));
            assertTrue(error.getMessage().contains("conversation_1786837272.txt"));
        }
    }

    private static JsonObject sample() throws IOException {
        return JsonParser.parseString(GeminiTakeoutFixture.json("1786837273")).getAsJsonObject();
    }

    private static chatmap.application.model.ImportedChat parse(JsonObject object) {
        return GeminiConversationParser.parse(object, "sample-id", "file:///sample.txt", importedAt);
    }

    @TempDir
    Path tempDir;
    private final GeminiTakeoutImporter reader = new GeminiTakeoutImporter();
    private static final String importedAt = "2026-09-10T23:00:00Z";
}
