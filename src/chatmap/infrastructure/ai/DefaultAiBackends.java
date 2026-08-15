package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiBackend;

import chatmap.infrastructure.command.CommandRunner;

import chatmap.application.port.command.CommandExecutor;

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
                "claude", StandardCliBackend.claude(executor, timeout),
                "codex", StandardCliBackend.codex(executor, timeout),
                "agy", StandardCliBackend.agy(executor, timeout),
                "ollama", new OllamaCliBackend(executor, timeout, "llama3"),
                "jshell", new JShellBackend()
        );
    }
}
