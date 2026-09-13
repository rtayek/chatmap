package chatmap.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.metadata.ProjectMetadataValidator.Result;

class ProjectMetadataValidatorTest {

    @TempDir
    Path root;

    @Test
    void validPilotPasses() throws IOException {
        writeValidTree(root);
        Result result = new ProjectMetadataValidator(root).validate();
        assertTrue(result.passed(), () -> "expected pass but got: " + result.failures());
        assertEquals(6, result.pilotDocumentCount());
    }

    @Test
    void missingRequiredPathFails() throws IOException {
        writeValidTree(root);
        Files.delete(root.resolve(".llm/human.md"));
        assertFailure(".llm/human.md");
    }

    @Test
    void missingHandoffDirectoryFails() throws IOException {
        writeValidTree(root);
        deleteRecursively(root.resolve(".llm/handoffs"));
        assertFailure("handoff directory");
    }

    @Test
    void blankRequiredFieldFails() throws IOException {
        writeValidTree(root);
        writePilot(root, "design", "CM-DESIGN-01", "durable", "");
        assertFailure("'status' is missing or blank");
    }

    @Test
    void duplicateIdFails() throws IOException {
        writeValidTree(root);
        writePilot(root, "design", "CM-EVO-01", "durable", "active");
        assertFailure("is duplicated across");
    }

    @Test
    void invalidLifecycleFails() throws IOException {
        writeValidTree(root);
        writePilot(root, "design", "CM-DESIGN-01", "eternal", "active");
        assertFailure("lifecycle 'eternal'");
    }

    @Test
    void invalidStatusFails() throws IOException {
        writeValidTree(root);
        writePilot(root, "design", "CM-DESIGN-01", "durable", "bogus");
        assertFailure("status 'bogus'");
    }

    @Test
    void duplicateFrontMatterKeyFails() throws IOException {
        writeValidTree(root);
        String body = "---\nid: CM-DESIGN-01\nid: CM-DESIGN-99\nlifecycle: durable\n"
                + "status: active\nprovenance: git-history\n---\n# Design\n";
        Files.write(root.resolve(".llm/design.md"), body.getBytes(StandardCharsets.UTF_8));
        assertFailure("duplicate front-matter key 'id'");
    }

    @Test
    void bomFails() throws IOException {
        writeValidTree(root);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = validFrontMatter("CM-DESIGN-01", "durable", "active").getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(body, 0, withBom, bom.length, body.length);
        Files.write(root.resolve(".llm/design.md"), withBom);
        assertFailure("UTF-8 BOM");
    }

    @Test
    void crlfFails() throws IOException {
        writeValidTree(root);
        String body = validFrontMatter("CM-DESIGN-01", "durable", "active").replace("\n", "\r\n");
        Files.write(root.resolve(".llm/design.md"), body.getBytes(StandardCharsets.UTF_8));
        assertFailure("CRLF");
    }

    @Test
    void missingFrontMatterFails() throws IOException {
        writeValidTree(root);
        Files.write(root.resolve(".llm/design.md"), "# Design\nno front matter\n".getBytes(StandardCharsets.UTF_8));
        assertFailure("does not begin with YAML front matter");
    }

    @Test
    void invalidUtf8Fails() throws IOException {
        writeValidTree(root);
        byte[] invalid = {(byte) 0xFF, (byte) 0xFE, 'x', '\n'};
        Files.write(root.resolve(".llm/design.md"), invalid);
        assertFailure("not valid UTF-8");
    }

    @Test
    void missingManifestFails() throws IOException {
        Result result = new ProjectMetadataValidator(root).validate();
        assertFalse(result.passed());
        assertTrue(result.failures().get(0).contains("manifest not found"));
    }

    @Test
    void missingValidationPolicyFails() throws IOException {
        writeValidTree(root);
        replaceManifest("\"validation\": {", "\"validation_removed\": {");
        assertFailure("validation block is missing");
    }

    @Test
    void unsupportedValidationEncodingFails() throws IOException {
        writeValidTree(root);
        replaceManifest("\"encoding\": \"UTF-8\"", "\"encoding\": \"UTF-16\"");
        assertFailure("validation.encoding must be 'UTF-8'");
    }

    @Test
    void disabledRequiredValidationFails() throws IOException {
        writeValidTree(root);
        replaceManifest("\"require_unique_ids\": true", "\"require_unique_ids\": false");
        assertFailure("validation.require_unique_ids must be true");
    }

    private void assertFailure(String expectedSubstring) throws IOException {
        Result result = new ProjectMetadataValidator(root).validate();
        assertFalse(result.passed(), "expected a failure containing: " + expectedSubstring);
        assertTrue(result.failures().stream().anyMatch(failure -> failure.contains(expectedSubstring)),
                () -> "no failure contained '" + expectedSubstring + "'; got: " + result.failures());
    }

    private void replaceManifest(String expected, String replacement) throws IOException {
        Path path = root.resolve(".llm/manifest.json");
        String manifest = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(manifest.contains(expected), () -> "manifest did not contain: " + expected);
        Files.writeString(path, manifest.replace(expected, replacement), StandardCharsets.UTF_8);
    }

    private static void writeValidTree(Path root) throws IOException {
        Files.createDirectories(root.resolve(".llm/handoffs"));
        Files.writeString(root.resolve(".llm/manifest.json"), MANIFEST, StandardCharsets.UTF_8);
        writePilot(root, "index", "CM-IDX-01", "durable", "active");
        writePilot(root, "first-principles", "CM-FP-01", "durable", "active");
        writePilot(root, "design", "CM-DESIGN-01", "durable", "active");
        writePilot(root, "evo", "CM-EVO-01", "durable", "active");
        writePilot(root, "implementation-notes", "CM-IMPL-01", "working", "active");
        writePilot(root, "working-context", "CM-CTX-01", "working", "active");
        Files.writeString(root.resolve(".llm/human.md"), "# Human\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve(".llm/persona.md"), "# Persona\n", StandardCharsets.UTF_8);
    }

    private static void writePilot(Path root, String name, String id, String lifecycle, String status)
            throws IOException {
        Files.write(root.resolve(".llm/" + name + ".md"),
                validFrontMatter(id, lifecycle, status).getBytes(StandardCharsets.UTF_8));
    }

    private static String validFrontMatter(String id, String lifecycle, String status) {
        return "---\nid: " + id + "\nlifecycle: " + lifecycle + "\nstatus: " + status
                + "\nprovenance: git-history\n---\n# Document\n";
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
    }

    private static final String MANIFEST = """
            {
              "format_version": 1,
              "project_id": "CHATMAP",
              "entrypoint": ".llm/index.md",
              "required_documents": [
                ".llm/index.md",
                ".llm/human.md",
                ".llm/persona.md",
                ".llm/first-principles.md",
                ".llm/design.md",
                ".llm/evo.md",
                ".llm/implementation-notes.md",
                ".llm/working-context.md"
              ],
              "handoff_directory": ".llm/handoffs",
              "excluded_paths": [".chatmap-local/"],
              "metadata_pilot": {
                "documents": [
                  ".llm/index.md",
                  ".llm/first-principles.md",
                  ".llm/design.md",
                  ".llm/evo.md",
                  ".llm/implementation-notes.md",
                  ".llm/working-context.md"
                ],
                "required_fields": ["id", "lifecycle", "status", "provenance"],
                "allowed_lifecycle": ["durable", "working"],
                "allowed_status": ["active", "retired", "superseded", "uncertain"]
              },
              "validation": {
                "encoding": "UTF-8",
                "bom": false,
                "line_endings": "LF",
                "require_unique_ids": true,
                "require_paths_exist": true
              }
            }
            """;
}
