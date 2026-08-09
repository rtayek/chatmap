package chatmap.backend.providers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;

import chatmap.domain.ConversationCandidate;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;

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
public final class CodexCliHistoryProvider implements ChatProvider {

    private final Path root;

    public CodexCliHistoryProvider() {
        this(Path.of(System.getProperty("user.home"), ".codex", "sessions"));
    }

    CodexCliHistoryProvider(Path root) {
        this.root = root;
    }

    @Override
    public String name() {
        return "Codex (CLI)";
    }

    @Override
    public Optional<ImportedChat> latestChat() {
        return LocalCliSessions.newestSessionFile(root).flatMap(file -> buildFrom(root, file));
    }

    @Override
    public List<ConversationCandidate> listChats() throws Exception {
        List<ConversationCandidate> candidates = new ArrayList<>();
        for (Path file : LocalCliSessions.listSessionFiles(root)) {
            candidates.add(candidate(file));
        }
        return candidates;
    }

    @Override
    public ImportedChat fetch(ConversationCandidate candidate) {
        return buildFrom(root, root.resolve(candidate.externalConversationId()).normalize())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No importable Codex CLI session: " + candidate.externalConversationId()));
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
                Source.codexCli, ProviderIdentity.cliSessionId(root, file),
                LocalCliSessions.sourceUri(file)));
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

    private ConversationCandidate candidate(Path file) {
        Path fn = file.getFileName();
        String title = (fn != null) ? fn.toString().replaceFirst("\\.jsonl$", "") : "";
        String modifiedAt = LocalCliSessions.modifiedAt(file);
        return new ConversationCandidate(Source.codexCli, ProviderIdentity.cliSessionId(root, file),
                title, LocalCliSessions.sourceUri(file), modifiedAt);
    }
}
