package chatmap.application.port.ai;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import chatmap.domain.Source;

/** Curated user-selectable prompt targets. Stable ids are CLI/UI boundary values. */
public enum ModelTarget {
    claude("claude", "Claude", ProviderId.claudeCli, "default", Source.claudeCliPrompt),
    codex("codex", "Codex", ProviderId.codexCli, "default", Source.codexCliPrompt),
    agy("agy", "Antigravity", ProviderId.antigravityCli, "default", Source.agyCliPrompt),
    ollama("ollama", "Ollama llama3", ProviderId.ollama, "llama3", Source.ollamaPrompt),
    ollamaGlm4("ollama-glm4", "Ollama GLM4 9B", ProviderId.ollama, "glm4:9b", Source.ollamaPrompt),
    ollamaQwenOpenclaw("ollama-qwen-openclaw", "Ollama Qwen OpenClaw", ProviderId.ollama,
            "qwen-openclaw:latest", Source.ollamaPrompt),
    ollamaQwenOpenclawLarge("ollama-qwen-openclaw-large", "Ollama Qwen OpenClaw Large", ProviderId.ollama,
            "qwen-openclaw-large:latest", Source.ollamaPrompt),
    ollamaQwenOpenclawSmall("ollama-qwen-openclaw-small", "Ollama Qwen OpenClaw Small", ProviderId.ollama,
            "qwen-openclaw-small:latest", Source.ollamaPrompt),
    ollamaQwen2532k("ollama-qwen2.5-32k", "Ollama Qwen 2.5 32K", ProviderId.ollama,
            "qwen2.5-32k:latest", Source.ollamaPrompt),
    ollamaQwen257b("ollama-qwen2.5-7b", "Ollama Qwen 2.5 7B", ProviderId.ollama,
            "qwen2.5:7b", Source.ollamaPrompt),
    jshell("jshell", "JShell Harness", ProviderId.jshell, "default", Source.jshellHarness);

    private final String id;
    private final String displayName;
    private final ProviderId providerId;
    private final String providerModelName;
    private final Source source;

    ModelTarget(String id, String displayName, ProviderId providerId, String providerModelName, Source source) {
        this.id = requireNonblank(id, "id");
        this.displayName = requireNonblank(displayName, "displayName");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.providerModelName = requireNonblank(providerModelName, "providerModelName");
        this.source = Objects.requireNonNull(source, "source");
    }

    static {
        Set<String> ids = new HashSet<>();
        for (ModelTarget target : values()) {
            if (!ids.add(target.id())) {
                throw new ExceptionInInitializerError("Duplicate model target id: " + target.id());
            }
        }
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public String providerModelName() {
        return providerModelName;
    }

    public Source source() {
        return source;
    }

    public static ModelTarget require(String id) {
        String normalized = id == null ? "" : id.trim();
        return Arrays.stream(values())
                .filter(target -> target.id.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown model target '" + id + "'. Available targets: "
                                + Arrays.stream(values()).map(ModelTarget::id).toList()));
    }

    private static String requireNonblank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
