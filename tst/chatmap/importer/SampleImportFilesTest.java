package chatmap.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import chatmap.domain.Source;

class SampleImportFilesTest {

    private static final String importedAt = "2026-07-06T00:00:00Z";

    @Test
    void plainTextSampleImports() throws Exception {
        String text = Files.readString(sample("plainTextSample.txt"));

        ImportedChat imported = new PlainTextImporter().importText(text, importedAt);

        assertEquals("ChatMap plain text sample", imported.chat().title());
        assertEquals(Source.plainText, imported.chat().source());
        assertEquals(2, imported.messages().size());
        assertEquals("user", imported.messages().get(0).role());
        assertEquals("assistant", imported.messages().get(1).role());
        assertTrue(imported.messages().getFirst().text().contains("ChatMap"));
    }

    @Test
    void markdownSampleImports() throws Exception {
        String markdown = Files.readString(sample("markdownSample.md"));

        ImportedChat imported = new MarkdownImporter().importMarkdown(markdown, "markdownSample.md", importedAt);

        assertEquals("ChatMap Markdown Sample", imported.chat().title());
        assertEquals(Source.markdown, imported.chat().source());
        assertEquals(2, imported.messages().size());
        assertEquals("user", imported.messages().get(0).role());
        assertEquals("assistant", imported.messages().get(1).role());
        assertTrue(imported.messages().getFirst().text().contains("ChatMap"));
    }

    @Test
    void chatGptSampleImports() throws Exception {
        String json = Files.readString(sample("chatGptSample.json"));

        ImportedChat imported = new ChatGptJsonImporter().importJson(json, importedAt);

        assertEquals("ChatMap ChatGPT Sample", imported.chat().title());
        assertEquals(Source.chatgptJson, imported.chat().source());
        assertEquals("2024-07-03T09:46:40Z", imported.chat().createdAt());
        assertEquals("user", imported.messages().get(1).role());
        assertEquals("assistant", imported.messages().get(2).role());
        assertTrue(imported.messages().get(1).text().contains("ChatMap"));
        assertTrue(imported.messages().get(1).rawJson().contains("\"id\": \"userMessage\""));
    }

    private static Path sample(String fileName) {
        return Path.of("samples", fileName);
    }
}
