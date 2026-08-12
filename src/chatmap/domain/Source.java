package chatmap.domain;

/**
 * Closed set of chat sources. Each importer or AI backend maps to exactly one member.
 *
 * dbValue is the string persisted in chats.source and is stable forever;
 * renaming an enum constant must never change its dbValue once real
 * databases exist. Naming convention for dbValues: lowerCamelCase.
 *
 * A source used by an AI backend to record a prompt/response exchange (the
 * "*Prompt" and jshellHarness members) must never be reused by an importer
 * that reads a real conversation history, and vice versa: source is the
 * namespace content-hash dedup (see ChatRepository.findBySourceAndContentHash)
 * operates within, so two unrelated origins sharing a source value could
 * dedup-collide with each other's chats.
 *
 * fromDbValue is forgiving: an unrecognized stored value maps to unknown
 * rather than throwing, so older app versions can open newer databases
 * without losing access to the user's chats.
 */
public enum Source {
    plainText("plainText", "Plain text"),
    markdown("markdown", "Markdown"),
    chatgptJson("chatgptJson", "ChatGPT JSON"),
    claudeWeb("claudeWeb", "Claude web"),
    chatGptWeb("chatGptWeb", "ChatGPT web"),
    geminiWeb("geminiWeb", "Gemini web"),
    claudeCode("claudeCode", "Claude Code"),
    codexCli("codexCli", "Codex CLI"),
    geminiCli("geminiCli", "Gemini CLI"),
    jshellHarness("jshellHarness", "JShell harness"),
    claudeCliPrompt("claudeCliPrompt", "Claude CLI prompt"),
    codexCliPrompt("codexCliPrompt", "Codex CLI prompt"),
    geminiCliPrompt("geminiCliPrompt", "Gemini CLI prompt"),
    agyCliPrompt("agyCliPrompt", "Antigravity CLI prompt"),
    ollamaPrompt("ollamaPrompt", "Ollama prompt"),
    unknown("unknown", "Unknown");

    private final String dbValue;
    private final String displayName;

    Source(String dbValue, String displayName) {
        this.dbValue = dbValue;
        this.displayName = displayName;
    }

    public String dbValue() {
        return dbValue;
    }

    public String displayName() {
        return displayName;
    }

    public static Source fromDbValue(String dbValue) {
        for (Source s : values()) {
            if (s.dbValue.equals(dbValue)) {
                return s;
            }
        }
        return unknown;
    }
}
