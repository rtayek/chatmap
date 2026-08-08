package chatmap.backend;

import chatmap.domain.Source;

public interface AiBackend {
    AiResponse ask(AiRequest request);

    default Source source() {
        return Source.plainText;
    }
}
