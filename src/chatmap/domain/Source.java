package chatmap.domain;

/**
 * Closed set of chat sources. Each importer maps to exactly one member.
 *
 * dbValue is the string persisted in chats.source and is stable forever;
 * renaming an enum constant must never change its dbValue once real
 * databases exist. Naming convention for dbValues: lowerCamelCase.
 *
 * fromDbValue is forgiving: an unrecognized stored value maps to unknown
 * rather than throwing, so older app versions can open newer databases
 * without losing access to the user's chats.
 */
public enum Source {
    plainText("plainText", false),
    markdown("markdown", false),
    chatgptJson("chatgptJson", false),
    claudeWeb("claudeWeb", true),
    chatGptWeb("chatGptWeb", true),
    geminiWeb("geminiWeb", true),
    claudeCode("claudeCode", true),
    codexCli("codexCli", true),
    geminiCli("geminiCli", true),
    unknown("unknown", false);

    private final String dbValue;
    private final boolean isProvider;

    Source(String dbValue, boolean isProvider) {
        this.dbValue = dbValue;
        this.isProvider = isProvider;
    }

    public String dbValue() {
        return dbValue;
    }

    public boolean isProvider() {
        return isProvider;
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
