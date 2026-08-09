package chatmap.backend.ai;

import java.io.IOException;

import chatmap.domain.Source;

public interface AiBackend {
    AiResponse ask(AiRequest request);

    default String ask(String prompt) throws IOException {
        return ask(AiRequest.of(prompt)).text();
    }

    default Source source() {
        return Source.plainText;
    }
}
