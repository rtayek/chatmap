package chatmap.application.port.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record LlmResponse(
        String text,
        BackendId backendId,
        Duration duration,
        Channel channel,
        String targetId,
        Optional<String> providerModelName,
        String providerSessionId
) {
    public LlmResponse {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(providerModelName, "providerModelName");
    }

    public LlmResponse(String text, BackendId backendId, Duration duration, ModelTarget target, String providerSessionId) {
        this(text, backendId, duration, target.channel(), target.id(), target.providerModelName(), providerSessionId);
    }

    public Optional<String> sessionId() {
        return Optional.ofNullable(providerSessionId).filter(value -> !value.isBlank());
    }
}
