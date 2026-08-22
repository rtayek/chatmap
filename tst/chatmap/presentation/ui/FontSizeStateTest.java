package chatmap.presentation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FontSizeStateTest {

    @Test
    void startsAtDefaultSize() {
        assertEquals(14, new FontSizeState().current());
    }

    @Test
    void clampsIncreaseAndDecreaseAtSupportedSizes() {
        FontSizeState state = new FontSizeState();

        assertEquals(16, state.increase());
        assertEquals(18, state.increase());
        assertEquals(18, state.increase());
        assertEquals(16, state.decrease());
        assertEquals(14, state.decrease());
        assertEquals(12, state.decrease());
        assertEquals(10, state.decrease());
        assertEquals(8, state.decrease());
        assertEquals(8, state.decrease());
    }

    @Test
    void ignoresUnsupportedExplicitSizeAndCanReset() {
        FontSizeState state = new FontSizeState();

        assertEquals(16, state.increase());
        assertEquals(16, state.set(9));
        assertEquals(8, state.set(8));
        assertEquals(14, state.reset());
    }
}
