package chatmap.backend;

import java.util.List;

public interface CommandBackedAiBackend extends AiBackend {
    CommandBackedRun askWithResult(AiRequest request);

    List<String> commandFor(AiRequest request);
}
