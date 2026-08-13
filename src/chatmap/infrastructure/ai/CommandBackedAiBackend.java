package chatmap.infrastructure.ai;

import chatmap.application.port.ai.AiBackend;
import chatmap.application.port.ai.AiRequest;

import java.util.List;

public interface CommandBackedAiBackend extends AiBackend {
    CommandBackedRun askWithResult(AiRequest request);

    List<String> commandFor(AiRequest request);
}
