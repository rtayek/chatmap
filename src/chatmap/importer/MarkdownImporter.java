package chatmap.importer;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;

/** Imports Markdown as a transcript when role prefixes are present. */
public final class MarkdownImporter {

    public static final String unknownRole = "unknown";
    private static final Pattern roleLine = Pattern.compile(
            "^\\s*(user|assistant|system):.*$",
            Pattern.CASE_INSENSITIVE);

    public ImportedChat importMarkdown(String markdown, String fallbackTitle, String importedAt) {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(fallbackTitle, "fallbackTitle");
        Objects.requireNonNull(importedAt, "importedAt");

        Chat chat = new Chat(0, null, Source.markdown, deriveTitle(markdown, fallbackTitle),
                null, null, importedAt, false);
        List<Message> messages = RolePrefixedTranscriptParser.parse(markdownWithoutTitleHeading(markdown));
        if (messages.isEmpty()) {
            messages = List.of(new Message(0, 0, unknownRole, markdown, 0, null, null));
        }
        return new ImportedChat(chat, messages);
    }

    private static String deriveTitle(String markdown, String fallbackTitle) {
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.substring(2).isBlank()) {
                return trimmed.substring(2).trim();
            }
        }
        return fallbackTitle;
    }

    private static String markdownWithoutTitleHeading(String markdown) {
        String[] lines = markdown.split("\\R", -1);
        int firstRoleLine = firstRoleLine(lines);
        for (int i = 0; i < lines.length; i++) {
            if (i > firstRoleLine) {
                break;
            }
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("# ") && !trimmed.substring(2).isBlank()) {
                int next = i + 1;
                while (next < lines.length && lines[next].isBlank()) {
                    next++;
                }
                return joinWithoutRange(lines, i, next);
            }
        }
        return markdown;
    }

    private static int firstRoleLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (roleLine.matcher(lines[i]).matches()) {
                return i;
            }
        }
        return lines.length - 1;
    }

    private static String joinWithoutRange(String[] lines, int fromInclusive, int toExclusive) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i >= fromInclusive && i < toExclusive) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(lines[i]);
        }
        return result.toString();
    }
}
