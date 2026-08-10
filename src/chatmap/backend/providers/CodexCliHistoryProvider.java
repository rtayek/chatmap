package chatmap.backend.providers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import chatmap.domain.Source;

/**
 * Reads the most recent Codex CLI session as a chat.
 *
 * Sessions live at {@code ~/.codex/sessions/YYYY/MM/DD/rollout-*.jsonl} — one
 * rollout file per session, JSONL, one event per line. Verified against real
 * files on disk: the clean conversation turns are {@code event_msg} lines whose
 * {@code payload.type} is {@code user_message} (a user turn) or
 * {@code agent_message} (an assistant turn), each carrying the text in
 * {@code payload.message}. Everything else — reasoning, function_call /
 * function_call_output, the {@code response_item} developer/system messages,
 * session metadata — is transcript machinery, not conversation, and is skipped.
 */
public final class CodexCliHistoryProvider extends LocalCliHistoryProvider {

    public CodexCliHistoryProvider() {
        this(Path.of(System.getProperty("user.home"), ".codex", "sessions"));
    }

    CodexCliHistoryProvider(Path root) {
        super(root, Source.codexCli, "Codex (CLI)", "Codex CLI session");
    }

    @Override
    Parsed parseSession(Path file) {
        return new Parsed(null, parse(file));
    }

    static List<ClaudeTurn> parse(Path file) {
        List<ClaudeTurn> turns = new ArrayList<>();
        for (String line : SessionLines.read(file)) {
            JsonObject o = SessionLines.asObject(line);
            if (o == null || !o.has("payload") || !o.get("payload").isJsonObject()) {
                continue;
            }
            JsonObject payload = o.getAsJsonObject("payload");
            String payloadType = SessionLines.string(payload, "type");
            String role;
            if ("user_message".equals(payloadType)) {
                role = "user";
            } else if ("agent_message".equals(payloadType)) {
                role = "assistant";
            } else {
                continue;
            }
            String text = SessionLines.string(payload, "message");
            if (text != null && !text.isBlank()) {
                turns.add(new ClaudeTurn(role, text.strip()));
            }
        }
        return turns;
    }
}
