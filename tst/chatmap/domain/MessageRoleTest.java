package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MessageRoleTest {

    @Test
    void everyConstantRoundTripsThroughItsDbValue() {
        for (MessageRole role : MessageRole.values()) {
            assertEquals(role, MessageRole.fromDbValue(role.dbValue()),
                    "dbValue \"" + role.dbValue() + "\" must map back to " + role);
        }
    }

    @Test
    void unrecognizedDbValueFallsBackToUnknownRatherThanThrowing() {
        assertEquals(MessageRole.unknown, MessageRole.fromDbValue("someFutureRoleNotYetInvented"));
    }

    @Test
    void nullDbValueFallsBackToUnknown() {
        assertEquals(MessageRole.unknown, MessageRole.fromDbValue(null));
    }

    @Test
    void dbValueLookupIsCaseSensitive() {
        // "user" is a real dbValue; the differently-cased variant must not match it.
        assertEquals(MessageRole.unknown, MessageRole.fromDbValue("User"));
    }

    @Test
    void persistedValuesRemainStable() {
        // Explicit literals, not round-trips: a round-trip test can pass even if a
        // dbValue is accidentally changed, since fromDbValue would still map it back
        // to the same constant. These assertions pin the actual stored strings.
        assertEquals("user", MessageRole.user.dbValue());
        assertEquals("assistant", MessageRole.assistant.dbValue());
        assertEquals("system", MessageRole.system.dbValue());
        assertEquals("tool", MessageRole.tool.dbValue());
        assertEquals("unknown", MessageRole.unknown.dbValue());
    }
}
