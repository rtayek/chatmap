package chatmap.application.service;

/** The result of a git operation managed by {@link GitWorkspaceManager}. */
record GitOutcome(boolean success, String errorDetail) {
    static GitOutcome of(chatmap.application.port.command.CommandResult result, GitWorkspaceManager gitManager) {
        return new GitOutcome(result.exitCode() == 0, result.exitCode() == 0 ? null : gitManager.commandFailureDetail(result));
    }
}
