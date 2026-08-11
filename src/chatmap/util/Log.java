package chatmap.util;

import java.util.logging.Logger;

/**
 * Thin entry point to {@code java.util.logging} for code shared between the GUI and CLI
 * (or GUI-only code), where a bare {@code System.out}/{@code System.err} print can vanish
 * with no attached console. CLI entry points print directly instead -- a terminal is
 * exactly who's meant to see that output.
 */
public final class Log {

    private Log() {
    }

    public static Logger of(Class<?> owner) {
        return Logger.getLogger(owner.getName());
    }
}
