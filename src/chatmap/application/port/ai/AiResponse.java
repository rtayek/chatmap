package chatmap.application.port.ai;

import java.time.Duration;
import java.util.Objects;

public record AiResponse(
        String text,
        BackendId backendId,
        Duration duration
) {
    public AiResponse {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(duration, "duration");
    }
}
