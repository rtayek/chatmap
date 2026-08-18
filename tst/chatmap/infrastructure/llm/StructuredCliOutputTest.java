package chatmap.infrastructure.llm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructuredCliOutputTest {

    @Test
    void throwsStructuredOutputExceptionWhenJsonPresentButUnrecognized() {
        String stdout = "{\"type\":\"system\",\"status\":\"starting\"}\n" +
                        "{\"type\":\"telemetry\",\"memory\":1024}\n";
        StructuredOutputException exception = assertThrows(
                StructuredOutputException.class,
                () -> StructuredCliOutput.parse(stdout, "fallback-123")
        );
        assertTrue(exception.getMessage().contains("Agent produced structured output but no recognized response text"));
        assertTrue(exception.getMessage().contains("telemetry"));
    }

    @Test
    void plainTextPassesThrough() {
        String stdout = "This is just plain text\nwith multiple lines\nand no json.";
        StructuredCliOutput.Parsed parsed = StructuredCliOutput.parse(stdout, "fallback-123");
        assertEquals(stdout, parsed.text());
        assertEquals("fallback-123", parsed.sessionId());
    }

    @Test
    void recognizedTextWorks() {
        String stdout = "{\"type\":\"result\",\"result\":\"This is the answer.\"}\n";
        StructuredCliOutput.Parsed parsed = StructuredCliOutput.parse(stdout, "fallback-123");
        assertEquals("This is the answer.", parsed.text());
        assertEquals("fallback-123", parsed.sessionId());
    }

    @Test
    void extractsSessionId() {
        String stdout = "{\"type\":\"result\",\"result\":\"answer\",\"session_id\":\"session-456\"}\n";
        StructuredCliOutput.Parsed parsed = StructuredCliOutput.parse(stdout, null);
        assertEquals("answer", parsed.text());
        assertEquals("session-456", parsed.sessionId());
    }
}
