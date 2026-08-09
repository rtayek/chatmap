package chatmap.backend.web;

import chatmap.backend.providers.ProviderIdentity;

import chatmap.backend.providers.ChatProvider;

import chatmap.backend.providers.ClaudeTurn;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import chatmap.importer.ImportedChat;

/**
 * A {@link ChatProvider} that reads the latest gemini.google.com conversation
 * in-process over CDP. Sibling of {@link ClaudeWebChatProvider} for Gemini.
 * The underlying {@link GeminiWebAdapter} was verified against the live page.
 */
public final class GeminiWebChatProvider implements ChatProvider {

    static final String FALLBACK_TITLE = "Gemini (web) live chat";

    private final ChromeCdpLauncher launcher;
    private final GeminiWebAdapter adapter;
    private final String cdpUrl;

    public GeminiWebChatProvider() {
        this(ClaudeWebChatProvider.DEFAULT_CDP_URL);
    }

    public GeminiWebChatProvider(String cdpUrl) {
        this(new ChromeCdpLauncher(), new GeminiWebAdapter(cdpUrl), cdpUrl);
    }

    public GeminiWebChatProvider(ChromeCdpLauncher launcher, GeminiWebAdapter adapter, String cdpUrl) {
        this.launcher = launcher;
        this.adapter = adapter;
        this.cdpUrl = cdpUrl;
    }

    @Override
    public String name() {
        return "Gemini (web)";
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
                transcript.get().title(), transcript.get().url(),
                transcript.get().turns(), Instant.now().toString()));
    }

    public Optional<String> lastUnavailableReason() {
        return adapter.lastUnavailableReason();
    }

    static ImportedChat toImportedChat(String title, List<ClaudeTurn> turns, String importedAt) {
        return toImportedChat(title, null, turns, importedAt);
    }

    static ImportedChat toImportedChat(String title, String sourceUri, List<ClaudeTurn> turns, String importedAt) {
        return WebTranscripts.toImportedChat(title, turns, importedAt, FALLBACK_TITLE,
                chatmap.domain.Source.geminiWeb, ProviderIdentity.geminiWebId(sourceUri), sourceUri);
    }
}
