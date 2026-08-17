package chatmap.application.port.llm;

/**
 * Requested shape of a backend's raw output. A provider that doesn't support
 * a requested format must fail before launching its external process rather
 * than silently downgrading the request.
 */
public enum OutputFormat {
    text,
    streamJson
}
