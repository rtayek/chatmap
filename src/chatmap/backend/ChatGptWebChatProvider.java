package chatmap.backend;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import chatmap.importer.ImportedChat;

/**
 * A {@link ChatProvider} that reads the latest chatgpt.com conversation in-process
 * over CDP, from the already-logged-in Chrome (no server, no API key). Sibling of
 * {@link ClaudeWebChatProvider} for ChatGPT.
 */
public final class ChatGptWebChatProvider implements ChatProvider {

    static final String FALLBACK_TITLE = "ChatGPT (web) live chat";

    private final ChromeCdpLauncher launcher;
    private final ChatGptWebAdapter adapter;
    private final String cdpUrl;

    public ChatGptWebChatProvider() {
        this(ClaudeWebChatProvider.DEFAULT_CDP_URL);
    }

    public ChatGptWebChatProvider(String cdpUrl) {
        this(new ChromeCdpLauncher(), new ChatGptWebAdapter(cdpUrl), cdpUrl);
    }

    public ChatGptWebChatProvider(ChromeCdpLauncher launcher, ChatGptWebAdapter adapter, String cdpUrl) {
        this.launcher = launcher;
        this.adapter = adapter;
        this.cdpUrl = cdpUrl;
    }

    @Override
    public String name() {
        return "ChatGPT (web)";
    }

    @Override
    public Optional<ImportedChat> latestChat() {
        if (!launcher.ensureChromeRunning(cdpUrl, System.out)) {
            return Optional.empty();
        }
        Optional<CdpTranscriptAdapter.Transcript> transcript = adapter.latestTranscript();
        if (transcript.isEmpty() || transcript.get().turns().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toImportedChat(
                transcript.get().title(), transcript.get().turns(), Instant.now().toString()));
    }

    static ImportedChat toImportedChat(String title, List<ClaudeTurn> turns, String importedAt) {
        return WebTranscripts.toImportedChat(title, turns, importedAt, FALLBACK_TITLE);
    }
}
