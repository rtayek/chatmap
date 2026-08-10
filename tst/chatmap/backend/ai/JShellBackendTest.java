package chatmap.backend.ai;

import chatmap.domain.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JShellBackendTest {

    private final JShellBackend backend = new JShellBackend();

    @Test
    void sourceIsPresentInEnumAndMatchesBackend() {
        assertEquals(Source.jshellHarness, Source.fromDbValue("jshellHarness"));
        assertEquals(Source.jshellHarness, backend.source());
    }

    @Test
    void evaluatesPrintStatementAndReturnsTen() {
        AiResponse response = backend.ask(AiRequest.of("int x = 5 + 5; System.out.print(x);"));

        assertEquals("10", response.text());
        assertEquals("JShell Harness", response.backendId().value());
        assertNotNull(response.duration());
    }

    @Test
    void evaluatesExpressionSnippetAndReturnsValue() {
        AiResponse response = backend.ask(AiRequest.of("5 + 5"));

        assertEquals("10", response.text());
    }

    @Test
    void extractsCodeFromMarkdownCodeBlock() {
        String input = """
                Here is the code to calculate:
                ```java
                int x = 5 + 5;
                System.out.print(x);
                ```
                """;

        AiResponse response = backend.ask(AiRequest.of(input));

        assertEquals("10", response.text());
    }

    @Test
    void handlesCompileErrorGracefully() {
        AiResponse response = backend.ask(AiRequest.of("int x = ;"));

        assertTrue(response.text().contains("Compile Error:"));
    }

    @Test
    void handlesRuntimeExceptionGracefully() {
        AiResponse response = backend.ask(AiRequest.of("System.out.print(1 / 0);"));

        assertTrue(response.text().contains("Exception:") || response.text().contains("ArithmeticException"));
    }
}
