package chatmap.backend;

import java.util.List;

/**
 * The ordered list of live/history chat providers both front ends use, defined
 * in one place so the CLI and the JavaFX app never drift apart.
 *
 * {@code LiveChatFetchService} tries providers in order and takes the first that
 * yields a chat, so order is priority. The chosen order:
 *
 * <ol>
 *   <li>{@link ClaudeWebChatProvider} — the live claude.ai conversation. First
 *       because it is the "latest live chat" this feature was built around; if
 *       nothing is reachable it returns empty quickly and we move on.</li>
 *   <li>{@link ClaudeCodeHistoryProvider} — this project's own CLI tool, the most
 *       likely local session to want.</li>
 *   <li>{@link CodexCliHistoryProvider}</li>
 *   <li>{@link GeminiCliHistoryProvider}</li>
 * </ol>
 *
 * The three CLI-history providers only read files already on disk, so listing
 * them costs nothing when their tool was never used (they return empty).
 */
public final class DefaultChatProviders {

    private DefaultChatProviders() {
    }

    public static List<ChatProvider> ordered() {
        return List.of(
                new ClaudeWebChatProvider(),
                new ClaudeCodeHistoryProvider(),
                new CodexCliHistoryProvider(),
                new GeminiCliHistoryProvider());
    }
}
