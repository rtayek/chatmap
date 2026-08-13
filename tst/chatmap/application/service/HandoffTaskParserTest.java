package chatmap.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import chatmap.domain.HandoffTask;

class HandoffTaskParserTest {

    private static final Path FILE = Path.of("inbox/chatmap/task.md");

    @Test
    void parsesFrontmatterAndBody() {
        String text = """
                ---
                agent: claude
                branch: feature-widget
                ---

                # Task Instructions

                Do the thing.
                """;

        HandoffTask task = HandoffTaskParser.parse(FILE, "chatmap", text);

        assertEquals(FILE, task.sourceFile());
        assertEquals("chatmap", task.projectKey());
        assertEquals("claude", task.agent());
        assertEquals("feature-widget", task.branch());
        assertEquals("# Task Instructions\n\nDo the thing.", task.body());
    }

    @Test
    void ignoresUnrecognizedFrontmatterFields() {
        String text = """
                ---
                agent: codex
                branch: fix-1
                extra: ignored
                ---
                body text
                """;

        HandoffTask task = HandoffTaskParser.parse(FILE, "chatmap", text);

        assertEquals("codex", task.agent());
        assertEquals("fix-1", task.branch());
    }

    @Test
    void missingOpeningDelimiterThrows() {
        String text = "agent: claude\nbranch: x\n---\nbody\n";

        HandoffTaskParseException thrown = assertThrows(HandoffTaskParseException.class,
                () -> HandoffTaskParser.parse(FILE, "chatmap", text));
        assertTrue(thrown.getMessage().contains("missing frontmatter"), thrown.getMessage());
    }

    @Test
    void unclosedFrontmatterThrows() {
        String text = "---\nagent: claude\nbranch: x\nno closing delimiter here\n";

        HandoffTaskParseException thrown = assertThrows(HandoffTaskParseException.class,
                () -> HandoffTaskParser.parse(FILE, "chatmap", text));
        assertTrue(thrown.getMessage().contains("never closed"), thrown.getMessage());
    }

    @Test
    void missingAgentThrows() {
        String text = "---\nbranch: x\n---\nbody\n";

        HandoffTaskParseException thrown = assertThrows(HandoffTaskParseException.class,
                () -> HandoffTaskParser.parse(FILE, "chatmap", text));
        assertTrue(thrown.getMessage().contains("'agent'"), thrown.getMessage());
    }

    @Test
    void missingBranchThrows() {
        String text = "---\nagent: claude\n---\nbody\n";

        HandoffTaskParseException thrown = assertThrows(HandoffTaskParseException.class,
                () -> HandoffTaskParser.parse(FILE, "chatmap", text));
        assertTrue(thrown.getMessage().contains("'branch'"), thrown.getMessage());
    }

    @Test
    void blankAgentValueThrows() {
        String text = "---\nagent: \nbranch: x\n---\nbody\n";

        assertThrows(HandoffTaskParseException.class,
                () -> HandoffTaskParser.parse(FILE, "chatmap", text));
    }
}
