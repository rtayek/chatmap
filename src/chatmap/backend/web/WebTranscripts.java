package chatmap.backend.web;

import chatmap.backend.providers.ClaudeTurn;

import java.util.ArrayList;
import java.util.List;

import chatmap.domain.Chat;
import chatmap.domain.ImportMetadata;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;

/**
 * Browser-free helpers shared by the web transcript adapters/providers: merging
 * turns, cleaning titles, and building an {@link ImportedChat}. Kept separate so
 * the logic that most needs testing does not require a live browser.
 */
final class WebTranscripts {

    private WebTranscripts() {
    }

    /** Merges runs of same-role turns into one, so a multi-paragraph answer is one message. */
    static List<ClaudeTurn> mergeConsecutiveSameRole(List<ClaudeTurn> turns) {
        List<ClaudeTurn> merged = new ArrayList<>();
        for (ClaudeTurn turn : turns) {
            if (!merged.isEmpty() && merged.get(merged.size() - 1).role().equals(turn.role())) {
                ClaudeTurn previous = merged.remove(merged.size() - 1);
                merged.add(new ClaudeTurn(previous.role(), previous.text() + "\n\n" + turn.text()));
            } else {
                merged.add(turn);
            }
        }
        return merged;
    }

    /** Joins the distinct non-blank lines of a value, collapsing adjacent repeats (split titles). */
    static String collapseRepeatedLines(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String previous = null;
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.equals(previous)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(trimmed);
            previous = trimmed;
        }
        return sb.toString();
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static String safeInnerText(CdpPage.CdpLocator locator) {
        try {
            return locator.innerText();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Builds an {@link ImportedChat} directly from role-ordered turns (no Markdown
     * intermediary). A blank title falls back to {@code fallbackTitle}.
     */
    static ImportedChat toImportedChat(String title, List<ClaudeTurn> turns, String importedAt,
            String fallbackTitle) {
        return toImportedChat(title, turns, importedAt, fallbackTitle, Source.markdown, null, null);
    }

    static ImportedChat toImportedChat(String title, List<ClaudeTurn> turns, String importedAt,
            String fallbackTitle, Source source, String externalConversationId, String sourceUri) {
        String chatTitle = (title == null || title.isBlank()) ? fallbackTitle : title.strip();
        Chat chat = new Chat(0, null, source, chatTitle, null, null, importedAt, false,
                new ImportMetadata(externalConversationId, sourceUri, null, null, importedAt));
        List<Message> messages = new ArrayList<>();
        int sequence = 0;
        for (ClaudeTurn turn : turns) {
            messages.add(new Message(0, 0, turn.role(), turn.text(), sequence++, null, null));
        }
        return new ImportedChat(chat, messages);
    }
}
