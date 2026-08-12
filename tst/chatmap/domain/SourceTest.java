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
}
