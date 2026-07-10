package chatmap.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chatmap.domain.Message;

final class RolePrefixedTranscriptParser {

    private static final Pattern roleLine = Pattern.compile(
            "^\\s*(user|assistant|system):\\s?(.*)$",
            Pattern.CASE_INSENSITIVE);

    private RolePrefixedTranscriptParser() {
    }

    static List<Message> parse(String text) {
        String[] lines = text.split("\\R", -1);
        List<String> preamble = new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        List<String> messageLines = null;
        String role = null;

        for (String line : lines) {
            Matcher matcher = roleLine.matcher(line);
            if (matcher.matches()) {
                if (messageLines != null) {
                    messages.add(message(messages.size(), role, messageLines));
                }
                role = matcher.group(1).toLowerCase(Locale.ROOT);
                messageLines = new ArrayList<>();
                if (messages.isEmpty() && !preamble.isEmpty()) {
                    messageLines.addAll(preamble);
                }
                if (!matcher.group(2).isEmpty()) {
                    messageLines.add(matcher.group(2));
                }
            } else if (messageLines == null) {
                preamble.add(line);
            } else {
                messageLines.add(line);
            }
        }

        if (messageLines != null) {
            messages.add(message(messages.size(), role, messageLines));
        }
        return List.copyOf(messages);
    }

    private static Message message(int sequence, String role, List<String> lines) {
        return new Message(0, 0, role, String.join("\n", lines), sequence, null, null);
    }
}
