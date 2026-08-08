package chatmap.exporter;

import chatmap.domain.Chat;

/**
 * Small shared Markdown-writing primitives for the exporters, so the header and
 * metadata formatting lives in one place rather than being re-implemented (and
 * drifting) in each exporter.
 */
final class Markdown {

    private Markdown() {
    }

    /** Appends an ATX heading of the given level plus the trailing blank line. */
    static void heading(StringBuilder out, int level, String text) {
        out.append("#".repeat(level)).append(' ').append(text).append("\n\n");
    }

    /** Appends the chat's {@code Source: <value>} line (always present). */
    static void sourceLine(StringBuilder out, Chat chat) {
        out.append("Source: ").append(chat.source().dbValue()).append("\n");
    }

    /** Appends {@code label: value} only when the value is non-blank. */
    static void metadata(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank()) {
            out.append(label).append(": ").append(value).append("\n");
        }
    }
}
