package chatmap.importer;

import java.util.List;
import java.util.Objects;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;

/** Minimal Markdown importer: one Markdown input becomes one chat and one message. */
public final class MarkdownImporter {

    public static final String unknownRole = "unknown";

    public ImportedChat importMarkdown(String markdown, String fallbackTitle, String importedAt) {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(fallbackTitle, "fallbackTitle");
        Objects.requireNonNull(importedAt, "importedAt");

        Chat chat = new Chat(0, null, Source.markdown, deriveTitle(markdown, fallbackTitle),
                null, null, importedAt, false);
        Message message = new Message(0, 0, unknownRole, markdown, 0, null, null);
        return new ImportedChat(chat, List.of(message));
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
}
