package chatmap.application.port.ai;

import java.util.Objects;
import java.util.Optional;

public record AiRequest(
        String prompt,
        Optional<String> systemPrompt,
        PromptProfile profile,
        Optional<String> sessionId
) {
    public AiRequest(String prompt, Optional<String> systemPrompt, PromptProfile profile) {
        this(prompt, systemPrompt, profile, Optional.empty());
    }

    public AiRequest(String prompt, Optional<String> systemPrompt) {
        this(prompt, systemPrompt, PromptProfile.general, Optional.empty());
    }

    public AiRequest {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sessionId, "sessionId");
    }

    public static AiRequest of(String prompt) {
        return new AiRequest(prompt, Optional.empty(), PromptProfile.general, Optional.empty());
    }

    public static AiRequest withProfile(String prompt, PromptProfile profile) {
        return new AiRequest(prompt, Optional.empty(), profile, Optional.empty());
    }

    public static AiRequest withSession(String prompt, String sessionId) {
        return new AiRequest(prompt, Optional.empty(), PromptProfile.general, Optional.ofNullable(sessionId));
    }

    public static AiRequest withSession(String prompt, String sessionId, PromptProfile profile) {
        return new AiRequest(prompt, Optional.empty(), profile, Optional.ofNullable(sessionId));
    }

    public static AiRequest withSystemPrompt(String prompt, String systemPrompt) {
        return new AiRequest(prompt, Optional.of(systemPrompt), PromptProfile.general, Optional.empty());
    }

    public String effectivePrompt() {
        return profile.applyTo(prompt);
    }
}
