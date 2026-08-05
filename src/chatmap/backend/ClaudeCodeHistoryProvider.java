package chatmap.backend;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import chatmap.importer.ImportedChat;

/**
 * Reads the most recent Claude Code CLI session as a chat.
 *
 * Sessions live at {@code ~/.claude/projects/<encoded-project>/<session-id>.jsonl}
 * — one JSONL file per session, one JSON object per line. Verified against real
 * files on disk: message lines have {@code type} = "user"/"assistant" and a
 * {@code message} object whose {@code content} is either a plain string or an
 * array of blocks (text / thinking / tool_use / tool_result). Only the text
 * content of user and assistant turns is kept; thinking and tool blocks are
 * transcript noise, not conversation. A title comes from an {@code ai-title} or
 * {@code custom-title} line when present.
 */
public final class ClaudeCodeHistoryProvider implements ChatProvider {

    record Parsed(String title, List<ClaudeTurn> turns) {}

    @Override
    public String name() {
        return "Claude Code (CLI)";
    }

    @Override
    public Optional<ImportedChat> latestChat() {
        Path root = Path.of(System.getProperty("user.home"), ".claude", "projects");
        return LocalCliSessions.newestSessionFile(root).flatMap(ClaudeCodeHistoryProvider::buildFrom);
    }

    /** Parses one session file into an ImportedChat, or empty when it holds no message turns. */
    static Optional<ImportedChat> buildFrom(Path file) {
        Parsed parsed = parse(file);
        if (parsed.turns().isEmpty()) {
            return Optional.empty();
        }
        String title = parsed.title() != null && !parsed.title().isBlank()
                ? parsed.title()
                : fileTitle(file);
        return Optional.of(LocalCliSessions.toImportedChat(title, parsed.turns(), LocalCliSessions.modifiedAt(file)));
    }

    static Parsed parse(Path file) {
        List<ClaudeTurn> turns = new ArrayList<>();
        String title = null;
        for (String line : SessionLines.read(file)) {
            JsonObject o = SessionLines.asObject(line);
            if (o == null) {
                continue;
            }
            String type = SessionLines.string(o, "type");
            if ("ai-title".equals(type)) {
                title = SessionLines.string(o, "aiTitle");
            } else if ("custom-title".equals(type) && title == null) {
                title = SessionLines.string(o, "customTitle");
            } else if ("user".equals(type) || "assistant".equals(type)) {
                JsonObject message = o.has("message") && o.get("message").isJsonObject()
                        ? o.getAsJsonObject("message") : null;
                if (message == null) {
                    continue;
                }
                String text = textContent(message.get("content"));
                if (!text.isBlank()) {
                    turns.add(new ClaudeTurn(type, text.strip()));
                }
            }
        }
        return new Parsed(title, turns);
    }

    /** Text of a Claude Code content value: the string itself, or the concatenated text blocks. */
    private static String textContent(JsonElement content) {
        if (content == null || content.isJsonNull()) {
            return "";
        }
        if (content.isJsonPrimitive()) {
            return content.getAsString();
        }
        if (content.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement element : content.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject block = element.getAsJsonObject();
                if ("text".equals(SessionLines.string(block, "type"))) {
                    String text = SessionLines.string(block, "text");
                    if (text != null && !text.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        }
        return "";
    }

    private static String fileTitle(Path file) {
        return file.getFileName().toString().replaceFirst("\\.jsonl$", "");
    }
}
