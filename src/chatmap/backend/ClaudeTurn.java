package chatmap.backend;

import java.util.Objects;

/**
 * One turn of a claude.ai web conversation: who spoke and what they said.
 *
 * Kept free of any Playwright type so the transcript-to-chat conversion (and its
 * tests) do not need a browser on the classpath's hot path.
 */
public record ClaudeTurn(String role, String text) {
    public ClaudeTurn {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
    }
}
