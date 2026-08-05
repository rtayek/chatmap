package chatmap.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;

/**
 * Shared plumbing for the local CLI-history providers (Claude Code, Codex,
 * Gemini): find the newest session file under a tool's directory tree, and turn
 * parsed turns into an {@link ImportedChat}. The per-tool JSONL parsing differs
 * and lives in each provider; only these tool-agnostic steps are shared.
 */
final class LocalCliSessions {

    private LocalCliSessions() {
    }

    /**
     * The most recently modified {@code *.jsonl} session file anywhere under
     * {@code root} (these tools nest by project/date), or empty when the
     * directory does not exist or holds no session files.
     */
    static Optional<Path> newestSessionFile(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /** File modification time as a UTC ISO-8601 string, for the imported timestamp. */
    static String modifiedAt(Path file) {
        return Instant.ofEpochMilli(file.toFile().lastModified()).toString();
    }

    /**
     * Builds an {@link ImportedChat} directly from role-ordered turns — no
     * Markdown intermediary, same principle as the claude.ai and ChatGPT import
     * paths. Turns are stored in order with sequential positions.
     */
    static ImportedChat toImportedChat(String title, List<ClaudeTurn> turns, String importedAt) {
        Chat chat = new Chat(0, null, Source.markdown, title, null, null, importedAt, false);
        List<Message> messages = new ArrayList<>();
        int sequence = 0;
        for (ClaudeTurn turn : turns) {
            messages.add(new Message(0, 0, turn.role(), turn.text(), sequence++, null, null));
        }
        return new ImportedChat(chat, messages);
    }
}
