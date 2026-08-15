package chatmap.application.port.ai;

import java.util.List;

/**
 * An {@link AiBackend} implemented by shelling out to a local CLI. Exposes
 * the raw {@link CommandBackedRun} (including exit code and timeout state)
 * for callers that need more than just the response text -- e.g. archiving
 * or reporting the underlying command's outcome.
 */
public interface CommandBackedAiBackend extends AiBackend {
    CommandBackedRun askWithResult(AiRequest request);

    List<String> commandFor(AiRequest request);
}
