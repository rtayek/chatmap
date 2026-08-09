package chatmap.backend;

import java.time.Duration;
import java.util.Map;

/**
 * Factory for default AI backends available for prompt execution across CLI and UI entry points.
 */
public final class DefaultAiBackends {

    private DefaultAiBackends() {
    }

    public static Map<String, AiBackend> defaults() {
        return defaults(new CommandRunner(), Duration.ofMinutes(3));
    }

    public static Map<String, AiBackend> defaults(CommandExecutor executor, Duration timeout) {
        return Map.of(
                "claude", new ClaudeCliBackend(executor, timeout),
                "codex", new CodexCliBackend(executor, timeout),
                "agy", new AgyCliBackend(executor, timeout),
                "ollama", new OllamaCliBackend(executor, timeout, "llama3")
        );
    }
}
