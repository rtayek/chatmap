package chatmap.application.port.ai;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record AiResponse(
        String text,
        BackendId backendId,
        Duration duration,
        ProviderId providerId,
        String targetId,
        String providerModelName,
        String providerSessionId
) {
    public AiResponse {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(providerModelName, "providerModelName");
    }

    public AiResponse(String text, BackendId backendId, Duration duration) {
        this(text, backendId, duration, ProviderId.claudeCli, backendId.value(), "default", null);
    }

    public AiResponse(String text, BackendId backendId, Duration duration, ModelTarget target, String providerSessionId) {
        this(text, backendId, duration, target.providerId(), target.id(), target.providerModelName(), providerSessionId);
    }

    public Optional<String> sessionId() {
        return Optional.ofNullable(providerSessionId).filter(value -> !value.isBlank());
    }
}
