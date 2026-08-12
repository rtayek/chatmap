package chatmap.backend.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import chatmap.backend.providers.ChatProvider;
import chatmap.backend.providers.ClaudeTurn;
import chatmap.backend.providers.NoImportableContentException;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;

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
            String reason = adapter.lastUnavailableReason().map(r -> " (" + r + ")").orElse("");
            throw new NoImportableContentException("No importable " + name + " chat: " + candidate.sourceUri() + reason);
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
        return Optional.of(inventoryDiagnostic);
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
