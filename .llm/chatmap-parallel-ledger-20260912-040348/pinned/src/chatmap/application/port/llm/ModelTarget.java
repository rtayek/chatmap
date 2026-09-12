package chatmap.application.port.llm;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import chatmap.domain.Source;

/** Curated user-selectable prompt targets. Stable ids are CLI/UI boundary values. */
public enum ModelTarget {
    claude("claude", "Claude", Model.claude, Channel.claudeCli),
    codex("codex", "Codex", Model.codex, Channel.codexCli),
    agy("agy", "Antigravity", Model.agy, Channel.antigravityCli),
    ollama("ollama", "Ollama llama3", Model.llama3, Channel.ollama),
    ollamaGlm4("ollama-glm4", "Ollama GLM4 9B", Model.glm4, Channel.ollama),
    ollamaQwenOpenclaw("ollama-qwen-openclaw", "Ollama Qwen OpenClaw", Model.qwenOpenclaw, Channel.ollama),
    ollamaQwenOpenclawLarge("ollama-qwen-openclaw-large", "Ollama Qwen OpenClaw Large", Model.qwenOpenclawLarge, Channel.ollama),
    ollamaQwenOpenclawSmall("ollama-qwen-openclaw-small", "Ollama Qwen OpenClaw Small", Model.qwenOpenclawSmall, Channel.ollama),
    ollamaQwen2532k("ollama-qwen2.5-32k", "Ollama Qwen 2.5 32K", Model.qwen2532k, Channel.ollama),
    ollamaQwen257b("ollama-qwen2.5-7b", "Ollama Qwen 2.5 7B", Model.qwen257b, Channel.ollama),
    jshell("jshell", "JShell Harness", Model.jshell, Channel.jshell);

    private final String id;
    private final String displayName;
    private final Model model;
    private final Channel channel;

    ModelTarget(String id, String displayName, Model model, Channel channel) {
        this.id = requireNonblank(id, "id");
        this.displayName = requireNonblank(displayName, "displayName");
        this.model = Objects.requireNonNull(model, "model");
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    private static final Map<Model, EnumSet<Channel>> byModel = new EnumMap<>(Model.class);
    private static final Map<Channel, EnumSet<Model>> byChannel = new EnumMap<>(Channel.class);

    static {
        Set<String> ids = new HashSet<>();
        for (ModelTarget target : values()) {
            if (!ids.add(target.id())) {
                throw new ExceptionInInitializerError("Duplicate model target id: " + target.id());
            }

            EnumSet<Channel> channels =
                    byModel.computeIfAbsent(target.model, m -> EnumSet.noneOf(Channel.class));
            if (!channels.add(target.channel)) {
                throw new ExceptionInInitializerError(
                        "Duplicate model target pairing: " + target.model + " x " + target.channel);
            }

            EnumSet<Model> models =
                    byChannel.computeIfAbsent(target.channel, c -> EnumSet.noneOf(Model.class));
            models.add(target.model);
        }
    }

    public static EnumSet<Channel> channelsFor(Model model) {
        EnumSet<Channel> channels = byModel.get(model);
        return channels == null ? EnumSet.noneOf(Channel.class) : EnumSet.copyOf(channels);
    }

    public static EnumSet<Model> modelsFor(Channel channel) {
        EnumSet<Model> models = byChannel.get(channel);
        return models == null ? EnumSet.noneOf(Model.class) : EnumSet.copyOf(models);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName + " [" + id + "]";
    }

    public Model model() {
        return model;
    }

    public Channel channel() {
        return channel;
    }

    // Retained for backward compatibility during the refactoring process
    // Replaced Channel with Channel
    public Channel providerId() {
        return channel;
    }

    public Optional<String> providerModelName() {
        return model.providerModelName();
    }

    // The real data shows Source depends only on the channel. If that ever stops
    // being true — a model that changes provenance within the same channel — the
    // derivation would move to a Map<Pair<Model,Channel>, Source> lookup instead.
    // For now, it remains channel-keyed.
    public Source source() {
        return channel.source();
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
