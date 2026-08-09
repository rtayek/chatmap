package chatmap.backend.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CdpPageTest {

    @Test
    void quotesJavaScriptStringsDeterministically() {
        assertEquals("\"a\\\\b \\\"quoted\\\"\"", CdpPage.jsString("a\\b \"quoted\""));
    }

    @Test
    void invokesFunctionSnippetsForCdpRuntimeEvaluation() {
        assertEquals("(() => document.readyState)()", CdpPage.expression("() => document.readyState"));
        assertEquals("document.title", CdpPage.expression("document.title"));
    }
}
