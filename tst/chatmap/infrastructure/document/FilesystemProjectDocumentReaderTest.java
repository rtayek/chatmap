package chatmap.infrastructure.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.application.port.document.ProjectDocumentReader.DocumentResult.Status;

class FilesystemProjectDocumentReaderTest {

    @Test
    void reportsUnconfiguredMissingAndAvailableStates(@TempDir Path directory) throws Exception {
        assertEquals(Status.unavailable, new FilesystemProjectDocumentReader(null).readWorkingContext().status());
        assertEquals(Status.missing,
                new FilesystemProjectDocumentReader(directory).readWorkingContext().status());
        Path documentDirectory = directory.resolve(".llm");
        Files.createDirectories(documentDirectory);
        Path document = documentDirectory.resolve("working-context.md");
        Files.writeString(document, "state🙂", StandardCharsets.UTF_8);

        var result = new FilesystemProjectDocumentReader(directory).readWorkingContext();

        assertEquals(Status.available, result.status());
        assertEquals("state🙂", result.text());
        assertEquals(".llm/working-context.md", result.relativePath());
        assertNotNull(result.hash());
    }

    @Test
    void rejectsEscapingSymbolicLinkWhenSupported(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root/.llm"));
        Path outside = directory.resolve("outside.md");
        Files.writeString(outside, "outside", StandardCharsets.UTF_8);
        Path link = root.resolve("working-context.md");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            return;
        }

        var result = new FilesystemProjectDocumentReader(directory.resolve("root")).readWorkingContext();

        assertEquals(Status.invalidConfiguration, result.status());
    }
}
