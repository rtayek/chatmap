package chatmap.application.port.ai;

/** Explicit capabilities a provider may support for a selected model target. */
public enum AiCapability {
    sessions,
    systemPrompt,
    streamJson,
    fileEditing
}
