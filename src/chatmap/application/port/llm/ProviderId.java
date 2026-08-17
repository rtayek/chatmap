package chatmap.application.port.llm;

/** Closed set of provider/protocol families ChatMap knows how to invoke. */
public enum ProviderId {
    claudeCli,
    codexCli,
    antigravityCli,
    ollama,
    jshell
}
