package chatmap.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chatmap.domain.Message;
import chatmap.domain.MessageRole;

final class RolePrefixedTranscriptParser {

    private static final Pattern roleLine = Pattern.compile(
            "^\\s*(user|assistant|system):\\s?(.*)$",
            Pattern.CASE_INSENSITIVE);

    private RolePrefixedTranscriptParser() {
    }

    static List<Message> parse(String text) {
        String[] lines = text.split("\\R", -1);
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
                if (!matcher.group(2).isEmpty()) {
                    messageLines.add(matcher.group(2));
                }
            } else if (messageLines != null) {
                messageLines.add(line);
            }
        }

        if (messageLines != null) {
            messages.add(message(messages.size(), role, messageLines));
        }
        return List.copyOf(messages);
    }

    private static Message message(int sequence, String role, List<String> lines) {
        return new Message(0, 0, MessageRole.fromDbValue(role), String.join("\n", trimBlankLines(lines)),
                sequence, null, null);
    }

    private static List<String> trimBlankLines(List<String> lines) {
        int first = 0;
        int last = lines.size();
        while (first < last && lines.get(first).isBlank()) {
            first++;
        }
        while (last > first && lines.get(last - 1).isBlank()) {
            last--;
        }
        return lines.subList(first, last);
    }
}
