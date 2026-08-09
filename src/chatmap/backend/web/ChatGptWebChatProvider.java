package chatmap.backend.web;

import chatmap.backend.providers.ProviderIdentity;

import chatmap.backend.providers.ChatProvider;

import chatmap.backend.providers.ClaudeTurn;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import chatmap.domain.ConversationCandidate;
import chatmap.domain.Source;
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
                transcript.get().title(), transcript.get().url(),
                transcript.get().turns(), Instant.now().toString()));
    }

    @Override
    public List<ConversationCandidate> listChats() {
        if (!launcher.ensureChromeRunning(cdpUrl, System.out)) {
            return List.of();
        }
        return candidates(adapter.discoverableChats());
    }

    @Override
    public ImportedChat fetch(ConversationCandidate candidate) {
        CdpTranscriptAdapter.ChatWebSummary summary =
                new CdpTranscriptAdapter.ChatWebSummary(candidate.title(), candidate.sourceUri());
        Optional<CdpTranscriptAdapter.Transcript> transcript = adapter.transcript(summary);
        if (transcript.isEmpty()) {
            throw new IllegalArgumentException("No importable ChatGPT web chat: " + candidate.sourceUri());
        }
        return toImportedChat(transcript.get().title(), transcript.get().url(),
                transcript.get().turns(), Instant.now().toString());
    }

    @Override
    public boolean inventoryComplete() {
        return false;
    }

    @Override
    public Optional<String> inventoryDiagnostic() {
        return Optional.of("ChatGPT web inventory is limited to all discoverable sidebar links; "
                + "lazy-loaded, archived, or hidden conversations may be absent.");
    }

    public Optional<String> lastUnavailableReason() {
        return adapter.lastUnavailableReason();
    }

    static ImportedChat toImportedChat(String title, List<ClaudeTurn> turns, String importedAt) {
        return toImportedChat(title, null, turns, importedAt);
    }

    static ImportedChat toImportedChat(String title, String sourceUri, List<ClaudeTurn> turns, String importedAt) {
        return WebTranscripts.toImportedChat(title, turns, importedAt, FALLBACK_TITLE,
                chatmap.domain.Source.chatGptWeb, ProviderIdentity.chatGptWebId(sourceUri), sourceUri);
    }

    static List<ConversationCandidate> candidates(List<CdpTranscriptAdapter.ChatWebSummary> summaries) {
        Map<String, ConversationCandidate> byIdentity = new LinkedHashMap<>();
        for (CdpTranscriptAdapter.ChatWebSummary summary : summaries) {
            String id = ProviderIdentity.chatGptWebId(summary.url());
            String key = id == null ? summary.url() : id;
            byIdentity.putIfAbsent(key, new ConversationCandidate(
                    Source.chatGptWeb, id, cleanTitle(summary.title()), summary.url(), null));
        }
        return List.copyOf(byIdentity.values());
    }

    private static String cleanTitle(String title) {
        return title == null || title.isBlank() ? FALLBACK_TITLE : title.strip();
    }
}
