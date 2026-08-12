package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatOriginTest {

    @Test
    void everyConstantRoundTripsThroughItsDbValue() {
        for (ChatOrigin origin : ChatOrigin.values()) {
            assertEquals(origin, ChatOrigin.fromDbValue(origin.dbValue()),
                    "dbValue \"" + origin.dbValue() + "\" must map back to " + origin);
        }
    }

    @Test
    void unrecognizedDbValueFallsBackToUnknownRatherThanThrowing() {
        assertEquals(ChatOrigin.unknown, ChatOrigin.fromDbValue("someFutureOriginNotYetInvented"));
    }

    @Test
    void nullDbValueFallsBackToUnknown() {
        assertEquals(ChatOrigin.unknown, ChatOrigin.fromDbValue(null));
    }

    @Test
    void dbValueLookupIsCaseSensitive() {
        // "IMPORTED" is a real dbValue; the differently-cased variant must not match it.
        assertEquals(ChatOrigin.unknown, ChatOrigin.fromDbValue("imported"));
    }

    @Test
    void persistedValuesRemainStable() {
        // Explicit literals, not round-trips: a round-trip test can pass even if a
        // dbValue is accidentally changed, since fromDbValue would still map it back
        // to the same constant. These assertions pin the actual stored strings.
        assertEquals("IMPORTED", ChatOrigin.imported.dbValue());
        assertEquals("GENERATED", ChatOrigin.generated.dbValue());
        assertEquals("UNKNOWN", ChatOrigin.unknown.dbValue());
    }
}
