package chatmap.a2a.experiment;

import java.util.Locale;

import chatmap.application.port.llm.Channel;
import chatmap.application.port.llm.ModelTarget;

/** Runtime selection for the bounded A2A worker experiment. */
final class A2aExperimentSettings {
    private static final String DEFAULT_TARGET = "ollama-glm4";

    private A2aExperimentSettings() {
    }

    static boolean modelBacked() {
        return switch (setting("chatmap.a2a.worker", "CHATMAP_A2A_WORKER", "fake")
                .toLowerCase(Locale.ROOT)) {
            case "fake" -> false;
            case "ollama" -> true;
            default -> throw new IllegalArgumentException(
                    "CHATMAP_A2A_WORKER must be fake or ollama");
        };
    }

    static ModelTarget modelTarget() {
        String targetId = setting(
                "chatmap.a2a.ollama.target",
                "CHATMAP_A2A_OLLAMA_TARGET",
                DEFAULT_TARGET);
        ModelTarget target = ModelTarget.require(targetId);
        if (target.channel() != Channel.ollama) {
            throw new IllegalArgumentException(
                    "CHATMAP_A2A_OLLAMA_TARGET must name an Ollama target: " + targetId);
        }
        return target;
    }

    private static String setting(String propertyName, String environmentName, String fallback) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }
        return fallback;
    }
}
