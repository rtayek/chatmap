package chatmap.domain;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One parsed handoff markdown file discovered in an inbox repository: the
 * frontmatter fields the orchestrator needs plus the task body, and where on
 * disk the file came from. {@code projectKey} is the inbox subfolder name
 * the file lived under (e.g. "chatmap"), used to look up the target project
 * repository to run the task against.
 */
public record HandoffTask(
        Path sourceFile,
        String projectKey,
        String agent,
        String branch,
        String body) {

    public HandoffTask {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(projectKey, "projectKey");
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(body, "body");
    }
}
