package chatmap.importer;

import java.util.List;
import java.util.Objects;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;

/** Minimal plain text importer: one input text becomes one chat and one message. */
public final class PlainTextImporter {

    public static final String unknownRole = "unknown";

    public ImportedChat importText(String text, String importedAt) {
        return importText(deriveTitle(text), text, importedAt);
    }

    public ImportedChat importText(String title, String text, String importedAt) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(importedAt, "importedAt");

        Chat chat = new Chat(0, null, Source.plainText, title, null, null, importedAt, false);
        Message message = new Message(0, 0, unknownRole, text, 0, null, null);
        return new ImportedChat(chat, List.of(message));
    }

    private static String deriveTitle(String text) {
        Objects.requireNonNull(text, "text");
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
            }
        }
        return "Plain Text Import";
    }
}
