package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SourceTest {

    @Test
    void everyConstantRoundTripsThroughItsDbValue() {
        for (Source source : Source.values()) {
            assertEquals(source, Source.fromDbValue(source.dbValue()),
                    "dbValue \"" + source.dbValue() + "\" must map back to " + source);
        }
    }

    @Test
    void unrecognizedDbValueFallsBackToUnknownRatherThanThrowing() {
        assertEquals(Source.unknown, Source.fromDbValue("someFutureSourceNotYetInvented"));
    }

    @Test
    void nullDbValueFallsBackToUnknown() {
        assertEquals(Source.unknown, Source.fromDbValue(null));
    }

    @Test
    void dbValueLookupIsCaseSensitive() {
        // "claudeWeb" is a real dbValue; the differently-cased variant must not match it.
        assertEquals(Source.unknown, Source.fromDbValue("ClaudeWeb"));
    }

    @Test
    void persistedValuesRemainStable() {
        // Explicit literals, not round-trips: a round-trip test can pass even if a
        // dbValue is accidentally changed, since fromDbValue would still map it back
        // to the same constant. These assertions pin the actual stored strings.
        assertEquals("plainText", Source.plainText.dbValue());
        assertEquals("markdown", Source.markdown.dbValue());
        assertEquals("chatgptJson", Source.chatgptJson.dbValue());
        assertEquals("claudeWeb", Source.claudeWeb.dbValue());
        assertEquals("chatGptWeb", Source.chatGptWeb.dbValue());
        assertEquals("geminiWeb", Source.geminiWeb.dbValue());
        assertEquals("claudeCode", Source.claudeCode.dbValue());
        assertEquals("codexCli", Source.codexCli.dbValue());
        assertEquals("geminiCli", Source.geminiCli.dbValue());
        assertEquals("jshellHarness", Source.jshellHarness.dbValue());
        assertEquals("claudeCliPrompt", Source.claudeCliPrompt.dbValue());
        assertEquals("codexCliPrompt", Source.codexCliPrompt.dbValue());
        assertEquals("geminiCliPrompt", Source.geminiCliPrompt.dbValue());
        assertEquals("agyCliPrompt", Source.agyCliPrompt.dbValue());
        assertEquals("ollamaPrompt", Source.ollamaPrompt.dbValue());
        assertEquals("unknown", Source.unknown.dbValue());
    }
}
