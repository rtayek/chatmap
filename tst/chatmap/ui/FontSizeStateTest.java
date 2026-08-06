package chatmap.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FontSizeStateTest {

    @Test
    void startsAtDefaultSize() {
        assertEquals(20, new FontSizeState().current());
    }

    @Test
    void clampsIncreaseAndDecreaseAtSupportedSizes() {
        FontSizeState state = new FontSizeState();

        assertEquals(24, state.increase());
        assertEquals(28, state.increase());
        assertEquals(28, state.increase());
        assertEquals(24, state.decrease());
        assertEquals(20, state.decrease());
        assertEquals(16, state.decrease());
        assertEquals(16, state.decrease());
    }

    @Test
    void ignoresUnsupportedExplicitSizeAndCanReset() {
        FontSizeState state = new FontSizeState();

        assertEquals(24, state.increase());
        assertEquals(24, state.set(17));
        assertEquals(16, state.set(16));
        assertEquals(20, state.reset());
    }
}
