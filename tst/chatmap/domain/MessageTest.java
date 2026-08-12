package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void rejectsNullRole() {
        assertThrows(NullPointerException.class,
                () -> new Message(1L, 2L, null, "text", 0, null, null));
    }
}
