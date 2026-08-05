package chatmap.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chatmap.domain.Message;
import chatmap.importer.ImportedChat;

/**
 * Parser tests for the three CLI-history providers, using JSONL crafted to match
 * the real on-disk shapes verified against actual session files. No tool needs
 * to be installed; parsing runs against temp files.
 */
class CliHistoryProvidersTest {

    // --- shared: newest-file selection ---

    @Test
    void newestSessionFileFindsNewestJsonlRecursively(@TempDir Path dir) throws Exception {
        Path nested = dir.resolve("2026/08/04");
        Files.createDirectories(nested);
        Path older = nested.resolve("rollout-old.jsonl");
        Path newer = nested.resolve("rollout-new.jsonl");
        Files.writeString(older, "{}");
        Files.writeString(newer, "{}");
        older.toFile().setLastModified(1_000_000_000_000L);
        newer.toFile().setLastModified(2_000_000_000_000L);
        Files.writeString(dir.resolve("notes.txt"), "ignore me"); // non-jsonl ignored

        assertEquals(newer, LocalCliSessions.newestSessionFile(dir).orElseThrow());
    }

    @Test
    void newestSessionFileEmptyWhenDirectoryMissing(@TempDir Path dir) {
        assertTrue(LocalCliSessions.newestSessionFile(dir.resolve("does-not-exist")).isEmpty());
    }

    // --- Claude Code ---

    @Test
    void claudeCodeKeepsOnlyTextTurnsAndUsesAiTitle(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("session.jsonl");
        Files.write(file, List.of(
                "{\"type\":\"queue-operation\",\"content\":\"noise\"}",
                "{\"type\":\"ai-title\",\"aiTitle\":\"My Session\"}",
                "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":\"hello there\"}}",
                "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\",\"content\":"
                        + "[{\"type\":\"thinking\",\"thinking\":\"hmm\"},{\"type\":\"text\",\"text\":\"hi back\"}]}}",
                "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":"
                        + "[{\"type\":\"tool_result\",\"content\":\"tool noise\"}]}}"));

        ClaudeCodeHistoryProvider.Parsed parsed = ClaudeCodeHistoryProvider.parse(file);
        assertEquals("My Session", parsed.title());
        assertEquals(List.of("user", "assistant"),
                parsed.turns().stream().map(ClaudeTurn::role).toList());
        assertEquals("hello there", parsed.turns().get(0).text());
        assertEquals("hi back", parsed.turns().get(1).text(), "thinking block must be dropped");

        ImportedChat chat = ClaudeCodeHistoryProvider.buildFrom(file).orElseThrow();
        assertEquals("My Session", chat.chat().title());
        assertEquals(2, chat.messages().size());
    }

    @Test
    void claudeCodeEmptyWhenNoMessageTurns(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("empty.jsonl");
        Files.write(file, List.of("{\"type\":\"queue-operation\",\"content\":\"noise\"}"));
        assertTrue(ClaudeCodeHistoryProvider.buildFrom(file).isEmpty());
    }

    // --- Codex ---

    @Test
    void codexKeepsOnlyUserAndAgentMessages(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("rollout-x.jsonl");
        Files.write(file, List.of(
                "{\"type\":\"session_meta\",\"payload\":{\"session_id\":\"abc\"}}",
                "{\"type\":\"response_item\",\"payload\":{\"type\":\"message\",\"role\":\"developer\","
                        + "\"content\":[{\"type\":\"input_text\",\"text\":\"system noise\"}]}}",
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"user_message\",\"message\":\"how does vim work?\"}}",
                "{\"type\":\"response_item\",\"payload\":{\"type\":\"reasoning\",\"summary\":\"thinking\"}}",
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"agent_message\",\"message\":\"It has modes.\"}}",
                "{\"type\":\"response_item\",\"payload\":{\"type\":\"function_call\",\"name\":\"shell\"}}"));

        List<ClaudeTurn> turns = CodexCliHistoryProvider.parse(file);
        assertEquals(List.of("user", "assistant"), turns.stream().map(ClaudeTurn::role).toList());
        assertEquals("how does vim work?", turns.get(0).text());
        assertEquals("It has modes.", turns.get(1).text());
    }

    // --- Gemini ---

    @Test
    void geminiUsesLatestSnapshotSkipsContextAndMapsRoles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("session-x.jsonl");
        Files.write(file, List.of(
                "{\"sessionId\":\"a2a\",\"kind\":\"main\"}",
                // an earlier, superseded snapshot
                "{\"$set\":{\"messages\":[{\"type\":\"user\",\"content\":[{\"text\":\"old\"}]}]}}",
                // the newest snapshot wins
                "{\"$set\":{\"messages\":["
                        + "{\"type\":\"user\",\"content\":[{\"text\":\"<session_context>\\nsetup</session_context>\"}]},"
                        + "{\"type\":\"user\",\"content\":[{\"text\":\"real question\"}]},"
                        + "{\"type\":\"gemini\",\"content\":[{\"text\":\"real answer\"}]}"
                        + "]}}"));

        List<ClaudeTurn> turns = GeminiCliHistoryProvider.parse(file);
        assertEquals(2, turns.size(), "session_context and superseded snapshot dropped");
        assertEquals("user", turns.get(0).role());
        assertEquals("real question", turns.get(0).text());
        assertEquals("assistant", turns.get(1).role());
        assertEquals("real answer", turns.get(1).text());
    }

    @Test
    void geminiEmptyWhenOnlyContextNoise(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("ctx.jsonl");
        Files.write(file, List.of(
                "{\"$set\":{\"messages\":[{\"type\":\"user\",\"content\":"
                        + "[{\"text\":\"<session_context>\\nonly setup</session_context>\"}]}]}}"));
        assertTrue(GeminiCliHistoryProvider.buildFrom(file).isEmpty());
    }

    @Test
    void messagesGetSequentialPositions(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("rollout-seq.jsonl");
        Files.write(file, List.of(
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"user_message\",\"message\":\"a\"}}",
                "{\"type\":\"event_msg\",\"payload\":{\"type\":\"agent_message\",\"message\":\"b\"}}"));
        ImportedChat chat = CodexCliHistoryProvider.buildFrom(file).orElseThrow();
        List<Message> m = chat.messages();
        assertEquals(0, m.get(0).sequence());
        assertEquals(1, m.get(1).sequence());
        assertFalse(chat.chat().title().isBlank());
    }
}
