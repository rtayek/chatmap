package chatmap.application.port.llm;

/** Explicit capabilities a provider may support for a selected model target. */
public enum LlmCapability {
    sessions,
    systemPrompt,
    streamJson,
    fileEditing
}
