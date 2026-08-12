package chatmap.domain;

/**
 * Normalized role of a message after provider-specific input has been parsed.
 *
 * dbValue is the string persisted in messages.role and is stable forever.
 * displayName is independently changeable UI text.
 *
 * fromDbValue is forgiving: an unrecognized or null stored value maps to
 * unknown rather than throwing, so older app versions can open databases
 * written by newer ones (same convention as {@link Source#fromDbValue}).
 *
 * "tool" is not produced by any importer or provider today -- it is kept as
 * a defensively-complete closed-set member, the same way {@code unknown}
 * exists before anything actually needs it.
 */
public enum MessageRole {
    user("user", "User"),
    assistant("assistant", "Assistant"),
    system("system", "System"),
    tool("tool", "Tool"),
    unknown("unknown", "Unknown");

    private final String dbValue;
    private final String displayName;

    MessageRole(String dbValue, String displayName) {
        this.dbValue = dbValue;
        this.displayName = displayName;
    }

    public String dbValue() {
        return dbValue;
    }

    public String displayName() {
        return displayName;
    }

    public static MessageRole fromDbValue(String value) {
        for (MessageRole role : values()) {
            if (role.dbValue.equals(value)) {
                return role;
            }
        }
        return unknown;
    }
}
