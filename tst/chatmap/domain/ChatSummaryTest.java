package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ChatSummaryTest {

    @Test
    void fiveArgConstructorDefaultsContentHashToNull() {
        ChatSummary summary = new ChatSummary(1L, 2L, "a summary", "claude", "2026-01-01T00:00:00Z");

        assertNull(summary.contentHash());
    }
}
