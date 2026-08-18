package chatmap.application.port.llm;

import chatmap.domain.Source;

/** Closed set of provider/protocol families ChatMap knows how to invoke. */
public enum Channel {
    claudeCli(Source.claudeCliPrompt),
    codexCli(Source.codexCliPrompt),
    antigravityCli(Source.agyCliPrompt),
    ollama(Source.ollamaPrompt),
    jshell(Source.jshellHarness);

    private final Source source;

    Channel(Source source) {
        this.source = source;
    }

    public Source source() {
        return source;
    }
}
