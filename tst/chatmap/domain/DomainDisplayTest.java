package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import chatmap.application.port.llm.ModelTarget;

class DomainDisplayTest {

    @Test
    void projectPrintsNameAndOptionalRepositoryPath() {
        Project plain = new Project(1, "Foo", null, "2026-08-21T00:00:00Z", "2026-08-21T00:00:00Z");
        Project withPath = new Project(2, "Bar", null, "C:/work/bar",
                "2026-08-21T00:00:00Z", "2026-08-21T00:00:00Z");

        assertEquals("Foo", plain.toString());
        assertEquals("Bar (C:/work/bar)", withPath.toString());
    }

    @Test
    void tagPrintsName() {
        assertEquals("important", new Tag(1, "important").toString());
    }

    @Test
    void chatPrintsTitleSourceAndId() {
        Chat chat = new Chat(7, null, Source.plainText, "Build notes", null, null,
                "2026-08-21T00:00:00Z", false, new ImportMetadata(null, null, null, null, null),
                ChatOrigin.imported, null, null, Optional.empty(), null);

        assertEquals("Build notes [Plain text, id=7]", chat.toString());
    }

    @Test
    void modelTargetPrintsDisplayNameAndStableId() {
        assertEquals("Ollama Qwen 2.5 7B [ollama-qwen2.5-7b]",
                ModelTarget.ollamaQwen257b.toString());
    }
}
