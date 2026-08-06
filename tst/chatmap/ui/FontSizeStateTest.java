package chatmap.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FontSizeStateTest {

    @Test
    void startsAtDefaultSize() {
        assertEquals(16, new FontSizeState().current());
    }

    @Test
    void clampsIncreaseAndDecreaseAtSupportedSizes() {
        FontSizeState state = new FontSizeState();

        assertEquals(18, state.increase());
        assertEquals(20, state.increase());
        assertEquals(24, state.increase());
        assertEquals(28, state.increase());
        assertEquals(28, state.increase());
        assertEquals(24, state.decrease());
        assertEquals(20, state.decrease());
        assertEquals(18, state.decrease());
        assertEquals(16, state.decrease());
        assertEquals(14, state.decrease());
        assertEquals(14, state.decrease());
    }

    @Test
    void ignoresUnsupportedExplicitSizeAndCanReset() {
        FontSizeState state = new FontSizeState();

        assertEquals(18, state.increase());
        assertEquals(18, state.set(17));
        assertEquals(14, state.set(14));
        assertEquals(16, state.reset());
    }
}
