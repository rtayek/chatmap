package chatmap.application.port.llm;

import java.util.Optional;

public enum Model {
    claude(null),
    codex(null),
    agy(null),
    llama3("llama3"),
    glm4("glm4:9b"),
    qwenOpenclaw("qwen-openclaw:latest"),
    qwenOpenclawLarge("qwen-openclaw-large:latest"),
    qwenOpenclawSmall("qwen-openclaw-small:latest"),
    qwen2532k("qwen2.5-32k:latest"),
    qwen257b("qwen2.5:7b"),
    jshell(null);

    private final String providerModelName;

    Model(String providerModelName) {
        this.providerModelName = providerModelName;
    }

    public Optional<String> providerModelName() {
        return Optional.ofNullable(providerModelName);
    }
}
