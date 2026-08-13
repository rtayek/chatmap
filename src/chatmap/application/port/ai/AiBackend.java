package chatmap.application.port.ai;

import java.io.IOException;
import java.util.List;

import chatmap.domain.Source;

public interface AiBackend {
    AiResponse ask(AiRequest request);

    default String ask(String prompt) throws IOException {
        return ask(AiRequest.of(prompt)).text();
    }

    default Source source() {
        return Source.plainText;
    }

    /** Provider-owned session discovery; non-CLI backends have no sessions. */
    default List<String> listSessions() {
        return List.of();
    }
}
