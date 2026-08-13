package chatmap.presentation.ui;

import java.util.List;

final class FontSizeState {

    static final List<Integer> SIZES = List.of(14, 16, 18, 20, 24, 28);
    static final int DEFAULT_SIZE = 16;

    private int index;

    FontSizeState() {
        index = SIZES.indexOf(DEFAULT_SIZE);
        if (index < 0) {
            index = 0;
        }
    }

    int current() {
        return SIZES.get(index);
    }

    int increase() {
        if (index < SIZES.size() - 1) {
            index++;
        }
        return current();
    }

    int decrease() {
        if (index > 0) {
            index--;
        }
        return current();
    }

    int reset() {
        index = SIZES.indexOf(DEFAULT_SIZE);
        if (index < 0) {
            index = 0;
        }
        return current();
    }

    int set(int size) {
        int requested = SIZES.indexOf(size);
        if (requested >= 0) {
            index = requested;
        }
        return current();
    }
}
