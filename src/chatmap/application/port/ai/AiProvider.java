package chatmap.application.port.ai;

import java.util.Set;

/** Provider/protocol implementation for one or more curated {@link ModelTarget}s. */
public interface AiProvider {
    AiResponse execute(ModelTarget target, AiRequest request);

    Set<AiCapability> capabilities(ModelTarget target);

    default java.util.List<String> listSessions(ModelTarget target) {
        return java.util.List.of();
    }
}
