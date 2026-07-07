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
    plainText("plainText"),
    markdown("markdown"),
    chatgptJson("chatgptJson"),
    unknown("unknown");

    private final String dbValue;

    Source(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
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
