package chatmap.application.port.ai;

/**
 * Whether a backend should skip interactive permission/approval prompts.
 * {@code unrestricted} trades a real security boundary for unattended
 * execution -- callers should only request it when the invocation is
 * already isolated some other way (e.g. a disposable git worktree).
 */
public enum PermissionMode {
    standard,
    unrestricted
}
