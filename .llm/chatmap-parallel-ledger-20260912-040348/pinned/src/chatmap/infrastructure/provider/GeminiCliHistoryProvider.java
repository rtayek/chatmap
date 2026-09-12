package chatmap.infrastructure.provider;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import chatmap.domain.Source;

/**
 * Reads the most recent Gemini CLI session as a chat.
 *
 * Sessions live at {@code ~/.gemini/tmp/<project-hash>/chats/session-*.jsonl},
 * project-scoped. Verified against real files on disk, which mix two line
 * shapes:
 * <ul>
 *   <li>A MongoDB-style {@code $set.messages} update, carrying a full snapshot
 *       array of turns so far. In practice this appears once per file (the
 *       injected {@code <session_context>} setup turn) — later snapshots, if
 *       any, supersede everything accumulated since the last one.</li>
 *   <li>Standalone per-line turn records, {@code {type, content, id,
 *       timestamp, ...}} — one line per real turn, appended after the initial
 *       snapshot. This is where actual conversation content lives; a version
 *       of this parser that only read {@code $set.messages} would see just
 *       the one-message context snapshot and treat every real session as
 *       empty.</li>
 * </ul>
 * For both shapes, a message is {@code {type, content}}: {@code content} is
 * either a plain string (seen on {@code gemini}-type turns) or an array of
 * {@code {text}} blocks (seen on {@code user}-type turns; a
 * {@code functionResponse} block instead of {@code text} is tool-call
 * plumbing, not user text, and yields no text to keep). Only {@code user} and
 * {@code gemini}/{@code model} messages are kept as turns; the leading
 * {@code <session_context>} block Gemini injects is setup metadata, not
 * conversation, and is skipped.
 */
public final class GeminiCliHistoryProvider extends LocalCliHistoryProvider {

    public GeminiCliHistoryProvider() {
        this(Path.of(System.getProperty("user.home"), ".gemini", "tmp"));
    }

    GeminiCliHistoryProvider(Path root) {
        super(root, Source.geminiCli, "Gemini (CLI)", "Gemini CLI session");
    }

    @Override
    Parsed parseSession(Path file) {
        return new Parsed(null, parse(file));
    }

    static List<ClaudeTurn> parse(Path file) {
        List<JsonObject> messages = new ArrayList<>();
        for (String line : SessionLines.read(file)) {
            JsonObject o = SessionLines.asObject(line);
            if (o == null) {
                continue;
            }
            JsonArray snapshot = messagesSnapshot(o);
            if (snapshot != null) {
                // A full-snapshot update: replaces everything accumulated since the last one.
                messages.clear();
                for (JsonElement element : snapshot) {
                    if (element.isJsonObject()) {
                        messages.add(element.getAsJsonObject());
                    }
                }
            } else if (o.has("type") && o.has("content")) {
                // A standalone incremental turn, appended after the last snapshot.
                messages.add(o);
            }
        }

        List<ClaudeTurn> turns = new ArrayList<>();
        for (JsonObject message : messages) {
            String role = mapRole(SessionLines.string(message, "type"));
            if (role == null) {
                continue;
            }
            String text = messageText(message.get("content"));
            if (text.isBlank() || text.stripLeading().startsWith("<session_context>")) {
                continue; // injected setup context, or tool-call plumbing with no user text
            }
            turns.add(new ClaudeTurn(role, text.strip()));
        }
        return turns;
    }

    /** The {@code messages} array of a {@code $set.messages} line, or null if this line isn't one. */
    private static JsonArray messagesSnapshot(JsonObject line) {
        if (!line.has("$set") || !line.get("$set").isJsonObject()) {
            return null;
        }
        JsonObject set = line.getAsJsonObject("$set");
        return set.has("messages") && set.get("messages").isJsonArray() ? set.getAsJsonArray("messages") : null;
    }

    private static String mapRole(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "user" -> chatmap.domain.MessageRole.user.dbValue();
            case "gemini", "model", "assistant" -> chatmap.domain.MessageRole.assistant.dbValue();
            default -> null;
        };
    }

    /** Text of a Gemini message content: the string, or the concatenated {text} blocks. */
    private static String messageText(JsonElement content) {
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
                String text = SessionLines.string(element.getAsJsonObject(), "text");
                if (text != null && !text.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        return "";
    }
}
