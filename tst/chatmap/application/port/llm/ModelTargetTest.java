package chatmap.application.port.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class ModelTargetTest {

    @Test
    void stableIdsAreUniqueAndNonblank() {
        Set<String> ids = new HashSet<>();

        for (ModelTarget target : ModelTarget.values()) {
            assertTrue(!target.id().isBlank(), target.name());
            assertTrue(ids.add(target.id()), "duplicate target id " + target.id());
        }
    }

    @Test
    void everyTargetDeclaresUserAndProviderMetadata() {
        for (ModelTarget target : ModelTarget.values()) {
            assertTrue(!target.displayName().isBlank(), target.id());
            assertNotNull(target.channel(), target.id());
            assertNotNull(target.source(), target.id());
        }
    }

    @Test
    void stableIdLookupSucceeds() {
        assertEquals(ModelTarget.claude, ModelTarget.require("claude"));
        assertEquals(ModelTarget.codex, ModelTarget.require(" codex "));
    }

    @Test
    void unknownTargetNamesAvailableIds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ModelTarget.require("frog"));

        assertTrue(exception.getMessage().contains("Unknown model target 'frog'"), exception.getMessage());
        assertTrue(exception.getMessage().contains("claude"), exception.getMessage());
    }

    @Test
    void enumOrderDefinesDisplayOrder() {
        assertEquals(List.of("claude", "codex", "agy", "ollama", "ollama-glm4",
                "ollama-qwen-openclaw", "ollama-qwen-openclaw-large",
                "ollama-qwen-openclaw-small", "ollama-qwen2.5-32k",
                "ollama-qwen2.5-7b", "jshell"),
                java.util.Arrays.stream(ModelTarget.values()).map(ModelTarget::id).toList());
    }
}
