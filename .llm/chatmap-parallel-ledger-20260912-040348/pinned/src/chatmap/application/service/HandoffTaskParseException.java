package chatmap.application.service;

/**
 * A handoff markdown file could not be turned into a {@link chatmap.domain.HandoffTask}
 * -- missing or malformed frontmatter, or a required field (agent/branch) was blank.
 * Callers that process an inbox should catch this per-file, write a failure report
 * for that one file, and continue with the rest of the scan.
 */
public class HandoffTaskParseException extends RuntimeException {

    public HandoffTaskParseException(String message) {
        super(message);
    }
}
