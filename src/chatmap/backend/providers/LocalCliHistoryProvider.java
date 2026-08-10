package chatmap.backend.providers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.domain.ConversationCandidate;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;

/**
 * Common skeleton for providers that read a local CLI tool's own JSONL session
 * history as chats: locate the newest/all session files under the tool's
 * directory, hydrate one into an {@link ImportedChat}, list candidates, fetch
 * by id. Subclasses supply only what differs per tool: where sessions live
 * (via the constructor), which {@link Source} they map to, and how to parse
 * one session file's turns (and title, when the format carries one) out of
 * its own JSONL shape.
 */
abstract class LocalCliHistoryProvider implements ChatProvider {

    /** One session file's parsed turns, and its title when the format carries one. */
    record Parsed(String title, List<ClaudeTurn> turns) {
    }

    private final Path root;
    private final Source source;
    private final String displayName;
    private final String sessionNoun;

    LocalCliHistoryProvider(Path root, Source source, String displayName, String sessionNoun) {
        this.root = root;
        this.source = source;
        this.displayName = displayName;
        this.sessionNoun = sessionNoun;
    }

    /** Parses one session file's title (if the format carries one) and message turns. */
    abstract Parsed parseSession(Path file);

    @Override
    public final String name() {
        return displayName;
    }

    @Override
    public final Optional<ImportedChat> latestChat() {
        return LocalCliSessions.newestSessionFile(root).flatMap(file -> buildFrom(root, file));
    }

    @Override
    public final List<ConversationCandidate> listChats() throws ChatProviderException {
        List<ConversationCandidate> candidates = new ArrayList<>();
        try {
            for (Path file : LocalCliSessions.listSessionFiles(root)) {
                candidates.add(candidate(file));
            }
        } catch (IOException unreadable) {
            throw new ChatProviderException(sessionNoun + "s under " + root + " could not be listed", unreadable);
        }
        return candidates;
    }

    @Override
    public final ImportedChat fetch(ConversationCandidate candidate) {
        return buildFrom(root, root.resolve(candidate.externalConversationId()).normalize())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No importable " + sessionNoun + ": " + candidate.externalConversationId()));
    }

    final Optional<ImportedChat> buildFrom(Path file) {
        return buildFrom(file.getParent(), file);
    }

    final Optional<ImportedChat> buildFrom(Path root, Path file) {
        Parsed parsed = parseSession(file);
        if (parsed.turns().isEmpty()) {
            return Optional.empty();
        }
        String title = parsed.title() != null && !parsed.title().isBlank()
                ? parsed.title()
                : fileTitle(file);
        String modifiedAt = LocalCliSessions.modifiedAt(file);
        return Optional.of(LocalCliSessions.toImportedChat(title, parsed.turns(), modifiedAt,
                source, ProviderIdentity.cliSessionId(root, file), LocalCliSessions.sourceUri(file)));
    }

    static String fileTitle(Path file) {
        Path fn = file.getFileName();
        return (fn != null) ? fn.toString().replaceFirst("\\.jsonl$", "") : "";
    }

    private ConversationCandidate candidate(Path file) {
        String modifiedAt = LocalCliSessions.modifiedAt(file);
        return new ConversationCandidate(source, ProviderIdentity.cliSessionId(root, file),
                fileTitle(file), LocalCliSessions.sourceUri(file), modifiedAt);
    }
}
