package chatmap.infrastructure.provider.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import chatmap.application.port.provider.ChatProvider;
import chatmap.application.port.provider.ChatProviderException;
import chatmap.infrastructure.provider.ClaudeTurn;
import chatmap.application.port.provider.NoImportableContentException;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.Source;
import chatmap.application.model.ImportedChat;

/**
 * Common base class for CDP-based live web chat providers.
 */
public abstract class CdpWebChatProvider implements ChatProvider {
    
    public static final String DEFAULT_CDP_URL = "http://127.0.0.1:9222";

    private final String name;
    private final String fallbackTitle;
    private final Source source;
    private final ChromeCdpLauncher launcher;
    private final CdpTranscriptAdapter adapter;
    private final String cdpUrl;
    private final String inventoryDiagnostic;
    private volatile WebDiscoveryResult lastDiscovery;

    protected CdpWebChatProvider(
            String name,
            String fallbackTitle,
            Source source,
            ChromeCdpLauncher launcher,
            CdpTranscriptAdapter adapter,
            String cdpUrl,
            String inventoryDiagnostic) {
        this.name = name;
        this.fallbackTitle = fallbackTitle;
        this.source = source;
        this.launcher = launcher;
        this.adapter = adapter;
        this.cdpUrl = cdpUrl;
        this.inventoryDiagnostic = inventoryDiagnostic;
    }

    @Override
    public String name() {
        return name;
    }

    protected abstract String extractIdentity(String url);

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
            lastDiscovery = WebDiscoveryResult.unavailable(name, "Chrome CDP endpoint not reachable");
            return List.of();
        }
        lastDiscovery = adapter.discoverAll();
        return candidates(lastDiscovery.conversations());
    }

    @Override
    public ImportedChat fetch(ConversationCandidate candidate) throws ChatProviderException {
        CdpTranscriptAdapter.ChatWebSummary summary =
                new CdpTranscriptAdapter.ChatWebSummary(candidate.title(), candidate.sourceUri());
        Optional<CdpTranscriptAdapter.Transcript> transcript = adapter.transcript(summary);
        if (transcript.isEmpty()) {
            Optional<String> unavailableReason = adapter.lastUnavailableReason();
            if (unavailableReason.isPresent()) {
                throw new ChatProviderException(
                        "Could not read " + name + " chat " + candidate.sourceUri()
                                + ": " + unavailableReason.get());
            }
            throw new NoImportableContentException(
                    "No importable " + name + " chat: " + candidate.sourceUri());
        }
        return toImportedChat(transcript.get().title(), transcript.get().url(),
                transcript.get().turns(), Instant.now().toString());
    }

    /**
     * True only when the most recent {@link #listChats()} call reached a
     * verified terminal condition ({@link DiscoveryStatus#complete}). Before
     * the first call, or when it was incomplete/unavailable/failed, this is
     * false -- callers should not treat "no newly discovered chats" as
     * complete unless the underlying discovery actually proved it.
     */
    @Override
    public boolean inventoryComplete() {
        return lastDiscovery != null && lastDiscovery.status() == DiscoveryStatus.complete;
    }

    @Override
    public Optional<String> inventoryDiagnostic() {
        if (lastDiscovery == null) {
            return Optional.of(inventoryDiagnostic);
        }
        String reason = lastDiscovery.reason();
        return Optional.of(reason == null || reason.isBlank() ? inventoryDiagnostic : reason);
    }

    public Optional<String> lastUnavailableReason() {
        return adapter.lastUnavailableReason();
    }

    // Package-private for testing
    ImportedChat toImportedChat(String title, List<ClaudeTurn> turns, String importedAt) {
        return toImportedChat(title, null, turns, importedAt);
    }

    ImportedChat toImportedChat(String title, String sourceUri, List<ClaudeTurn> turns, String importedAt) {
        return WebTranscripts.toImportedChat(title, turns, importedAt, fallbackTitle,
                source, extractIdentity(sourceUri), sourceUri);
    }

    List<ConversationCandidate> candidates(List<CdpTranscriptAdapter.ChatWebSummary> summaries) {
        Map<String, ConversationCandidate> byIdentity = new LinkedHashMap<>();
        for (CdpTranscriptAdapter.ChatWebSummary summary : summaries) {
            String id = extractIdentity(summary.url());
            String key = id == null ? summary.url() : id;
            byIdentity.putIfAbsent(key, new ConversationCandidate(
                    source, id, cleanTitle(summary.title()), summary.url(), null));
        }
        return List.copyOf(byIdentity.values());
    }

    private String cleanTitle(String title) {
        return title == null || title.isBlank() ? fallbackTitle : title.strip();
    }
}
