package chatmap.backend;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import chatmap.importer.ImportedChat;

/**
 * Reads the most recent Gemini CLI session as a chat.
 *
 * Sessions live at {@code ~/.gemini/tmp/<project-hash>/chats/session-*.jsonl},
 * project-scoped. Verified against real files on disk: each line is a
 * MongoDB-style update; a {@code $set.messages} line carries a full snapshot of
 * the conversation so far (later snapshots supersede earlier ones), and each
 * message is {@code {type, content:[{text}]}}. Only {@code user} and
 * {@code gemini}/{@code model} messages are kept as turns; the leading
 * {@code <session_context>} block Gemini injects is setup metadata, not
 * conversation, and is skipped.
 *
 * NOTE: on the machine this was built against, only {@code user}-type messages
 * were present in the logs, so the assistant-role mapping ({@code gemini} /
 * {@code model} -> assistant) follows Gemini CLI's documented convention rather
 * than observed data; adjust if a real assistant turn shows a different type.
 */
public final class GeminiCliHistoryProvider implements ChatProvider {

    @Override
    public String name() {
        return "Gemini (CLI)";
    }

    @Override
    public Optional<ImportedChat> latestChat() {
        Path root = Path.of(System.getProperty("user.home"), ".gemini", "tmp");
        return LocalCliSessions.newestSessionFile(root).flatMap(file -> buildFrom(root, file));
    }

    static Optional<ImportedChat> buildFrom(Path file) {
        return buildFrom(file.getParent(), file);
    }

    static Optional<ImportedChat> buildFrom(Path root, Path file) {
        List<ClaudeTurn> turns = parse(file);
        if (turns.isEmpty()) {
            return Optional.empty();
        }
        Path fn = file.getFileName();
        String title = (fn != null) ? fn.toString().replaceFirst("\\.jsonl$", "") : "";
        String modifiedAt = LocalCliSessions.modifiedAt(file);
        return Optional.of(LocalCliSessions.toImportedChat(title, turns, modifiedAt,
                chatmap.domain.Source.geminiCli, ProviderIdentity.cliSessionId(root, file),
                file.toAbsolutePath().normalize().toUri().toString()));
    }

    static List<ClaudeTurn> parse(Path file) {
        JsonArray messages = latestMessagesSnapshot(file);
        if (messages == null) {
            return List.of();
        }
        List<ClaudeTurn> turns = new ArrayList<>();
        for (JsonElement element : messages) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject message = element.getAsJsonObject();
            String role = mapRole(SessionLines.string(message, "type"));
            if (role == null) {
                continue;
            }
            String text = messageText(message.get("content"));
            if (text.isBlank() || text.stripLeading().startsWith("<session_context>")) {
                continue; // injected setup context, not conversation
            }
            turns.add(new ClaudeTurn(role, text.strip()));
        }
        return turns;
    }

    /** The messages array from the last {@code $set.messages} line (the newest full snapshot). */
    private static JsonArray latestMessagesSnapshot(Path file) {
        JsonArray latest = null;
        for (String line : SessionLines.read(file)) {
            JsonObject o = SessionLines.asObject(line);
            if (o == null || !o.has("$set") || !o.get("$set").isJsonObject()) {
                continue;
            }
            JsonObject set = o.getAsJsonObject("$set");
            if (set.has("messages") && set.get("messages").isJsonArray()) {
                latest = set.getAsJsonArray("messages");
            }
        }
        return latest;
    }

    private static String mapRole(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "user" -> "user";
            case "gemini", "model", "assistant" -> "assistant";
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
