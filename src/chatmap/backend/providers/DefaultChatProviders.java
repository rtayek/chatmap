package chatmap.backend;

import java.util.List;

/**
 * The ordered list of live/history chat providers both front ends use, defined
 * in one place so the CLI and the JavaFX app never drift apart.
 *
 * {@code LiveChatFetchService} tries providers in order and takes the first that
 * yields a chat, so order is priority. Six sources: three vendors (Claude,
 * ChatGPT, Gemini) each in two access modes (web over CDP, local CLI history).
 * The chosen order — web providers first, then CLI histories:
 *
 * <ol>
 *   <li>{@link ClaudeWebChatProvider} — live claude.ai; first because it is the
 *       "latest live chat" this feature was built around, and it is the verified
 *       web reader.</li>
 *   <li>{@link ChatGptWebChatProvider} — live chatgpt.com.</li>
 *   <li>{@link GeminiWebChatProvider} — live gemini.google.com.</li>
 *   <li>{@link ClaudeCodeHistoryProvider} — this project's own CLI tool, the most
 *       likely local session to want.</li>
 *   <li>{@link CodexCliHistoryProvider}</li>
 *   <li>{@link GeminiCliHistoryProvider}</li>
 * </ol>
 *
 * The web providers share one CDP Chrome (the first launches it; the rest attach),
 * and each returns empty quickly when its site is not reachable/logged in. The
 * three CLI-history providers only read files already on disk, so they cost
 * nothing when their tool was never used.
 */
public final class DefaultChatProviders {

    private DefaultChatProviders() {
    }

    public static List<ChatProvider> ordered() {
        return List.of(
                new ClaudeWebChatProvider(),
                new ChatGptWebChatProvider(),
                new GeminiWebChatProvider(),
                new ClaudeCodeHistoryProvider(),
                new CodexCliHistoryProvider(),
                new GeminiCliHistoryProvider());
    }
}
