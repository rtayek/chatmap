package chatmap.application.port.llm;

import java.util.Set;

/** Provider/protocol implementation for one or more curated {@link ModelTarget}s. */
public interface LlmProvider {
    LlmResponse execute(ModelTarget target, LlmRequest request);

    Set<LlmCapability> capabilities(ModelTarget target);

    default java.util.List<String> listSessions(ModelTarget target) {
        return java.util.List.of();
    }
}
