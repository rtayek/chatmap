package chatmap.infrastructure.importer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class GeminiTakeoutFixture {
    private GeminiTakeoutFixture() {
    }

    public static String json(String id) throws IOException {
        try (var input = Objects.requireNonNull(GeminiTakeoutFixture.class.getResourceAsStream(
                "fixtures/gemini-takeout/conversation-" + id + ".json"))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static Path write(Path root, String id) throws IOException {
        Path history = Files.createDirectories(root.resolve("Gemini in Workspace/Conversation History"));
        Path file = history.resolve("conversation_" + id + ".txt");
        Files.writeString(file, json(id));
        return file;
    }
}
