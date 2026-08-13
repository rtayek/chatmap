package chatmap.infrastructure.provider;

import chatmap.application.port.provider.ChatProvider;

import chatmap.infrastructure.provider.web.GeminiWebChatProvider;

import chatmap.infrastructure.provider.web.ChatGptWebChatProvider;

import chatmap.infrastructure.provider.web.ClaudeWebChatProvider;

import java.util.List;

/**
 * The ordered list of live/history chat providers both front ends use, defined
 * in one place so the CLI and the JavaFX app never drift apart.
 *
 * {@code LiveChatFetchService} tries providers in order and takes the first that
 * yields a chat, so order is priority. Six sources: three vendors (Claude,
 * ChatGPT, Gemini) each in two access modes (local CLI history, web over CDP).
 * The chosen order — CLI histories first, then web providers:
 *
 * <ol>
 *   <li>{@link ClaudeCodeHistoryProvider} — this project's own CLI tool, the most
 *       likely local session to want.</li>
 *   <li>{@link CodexCliHistoryProvider}</li>
 *   <li>{@link GeminiCliHistoryProvider}</li>
 *   <li>{@link ClaudeWebChatProvider} — live claude.ai.</li>
 *   <li>{@link ChatGptWebChatProvider} — live chatgpt.com.</li>
 *   <li>{@link GeminiWebChatProvider} — live gemini.google.com.</li>
 * </ol>
 *
 * CLI-history providers read files already on disk (deterministic, no network,
 * no dependency on a vendor's unversioned web UI), so they run first and cost
 * nothing when their tool was never used. The web providers share one CDP
 * Chrome (the first launches it; the rest attach), and each returns empty
 * quickly when its site is not reachable/logged in.
 *
 * This is a plain list, not gated behind a flag, so a specific ordering or
 * subset can be tried by editing it directly (comment a provider out) rather
 * than needing an environment variable to change behavior.
 */
public final class DefaultChatProviders {

    private DefaultChatProviders() {
    }

    public static List<ChatProvider> ordered() {
        return List.of(
                new ClaudeCodeHistoryProvider(),
                new CodexCliHistoryProvider(),
                new GeminiCliHistoryProvider(),
                new ClaudeWebChatProvider(),
                new ChatGptWebChatProvider(),
                new GeminiWebChatProvider());
    }
}
