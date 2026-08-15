package chatmap.application.port.ai;

/**
 * Requested shape of a backend's raw output. A backend that doesn't support
 * a requested format silently falls back to {@code text} rather than
 * passing an unrecognized flag to its CLI.
 */
public enum OutputFormat {
    text,
    streamJson
}
