package chatmap.application.port.ai;

import java.util.List;

/** An {@link AiProvider} implemented by shelling out to a local command. */
public interface CommandBackedAiProvider extends AiProvider {
    CommandBackedRun executeWithResult(ModelTarget target, AiRequest request);

    List<String> commandFor(ModelTarget target, AiRequest request);
}
