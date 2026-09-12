package chatmap.application.port.llm;

import java.util.List;

/** An {@link LlmProvider} implemented by shelling out to a local command. */
public interface CommandBackedLlmProvider extends LlmProvider {
    CommandBackedRun executeWithResult(ModelTarget target, LlmRequest request);

    List<String> commandFor(ModelTarget target, LlmRequest request);
}
