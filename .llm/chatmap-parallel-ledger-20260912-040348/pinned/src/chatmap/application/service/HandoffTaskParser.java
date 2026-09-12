package chatmap.application.service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chatmap.domain.HandoffTask;

/**
 * Parses a handoff markdown file's YAML-style frontmatter
 * ({@code ---\nkey: value\n---\n<body>}) into a {@link HandoffTask}. Only the
 * trivial {@code key: value} line shape used by these files is supported --
 * deliberately not a general YAML parser, since the format itself is fixed
 * and simple (see {@code template.md} in the inbox repo).
 */
final class HandoffTaskParser {

    private HandoffTaskParser() {
    }

    static HandoffTask parse(Path sourceFile, String projectKey, String rawText) {
        List<String> lines = rawText.lines().toList();
        if (lines.isEmpty() || !lines.get(0).strip().equals("---")) {
            throw new HandoffTaskParseException(sourceFile + ": missing frontmatter (must start with ---)");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        int bodyStart = -1;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.strip().equals("---")) {
                bodyStart = i + 1;
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            fields.put(key, value);
        }
        if (bodyStart < 0) {
            throw new HandoffTaskParseException(sourceFile + ": frontmatter was never closed with a second ---");
        }

        String agent = fields.get("agent");
        String branch = fields.get("branch");
        if (agent == null || agent.isBlank()) {
            throw new HandoffTaskParseException(sourceFile + ": frontmatter is missing a nonblank 'agent' field");
        }
        if (branch == null || branch.isBlank()) {
            throw new HandoffTaskParseException(sourceFile + ": frontmatter is missing a nonblank 'branch' field");
        }

        String body = String.join("\n", lines.subList(bodyStart, lines.size())).strip();
        return new HandoffTask(sourceFile, projectKey, agent, branch, body);
    }
}
