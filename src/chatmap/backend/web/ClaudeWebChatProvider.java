package chatmap.backend.web;

import chatmap.backend.providers.ProviderIdentity;

import chatmap.backend.providers.ChatProvider;

import chatmap.backend.providers.ClaudeTurn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.ImportMetadata;
import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;

/**
 * A {@link ChatProvider} that reads the latest claude.ai conversation directly,
 * in-process, over CDP — no external server, no HTTP boundary, no environment
 * variables. A drop-in replacement for the old HttpChatProvider at the
 * {@link ChatProvider} interface.
 *
 * Because there is no longer a process boundary to cross, the transcript turns
 * are turned into an {@link ImportedChat} directly (as ChatGptJsonImporter does
 * from parsed data), skipping the Markdown round-trip the HTTP path needed.
 */
public final class ClaudeWebChatProvider implements ChatProvider {

    /** Chrome's default CDP endpoint, matching ChromeCdpLauncher / start-chrome-cdp-claude.sh. */
    public static final String DEFAULT_CDP_URL = "http://127.0.0.1:9222";

    private final ChromeCdpLauncher launcher;
    private final ClaudeWebAdapter adapter;
    private final String cdpUrl;

    public ClaudeWebChatProvider() {
        this(DEFAULT_CDP_URL);
    }

    public ClaudeWebChatProvider(String cdpUrl) {
        this(new ChromeCdpLauncher(), new ClaudeWebAdapter(cdpUrl), cdpUrl);
    }

    public ClaudeWebChatProvider(ChromeCdpLauncher launcher, ClaudeWebAdapter adapter, String cdpUrl) {
        this.launcher = launcher;
        this.adapter = adapter;
        this.cdpUrl = cdpUrl;
    }

    @Override
    public String name() {
        return "Claude (web)";
    }

    @Override
    public Optional<ImportedChat> latestChat() {
        // Make sure a logged-in Chrome is up first; "not up in time" is not an error.
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
            throw new IllegalArgumentException("No importable Claude web chat: " + candidate.sourceUri());
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
        return Optional.of("Claude web inventory is limited to all discoverable sidebar links; "
                + "lazy-loaded or archived conversations may be absent.");
    }

    public Optional<String> lastUnavailableReason() {
        return adapter.lastUnavailableReason();
    }

    /**
     * Builds an {@link ImportedChat} directly from conversation turns — no
     * Markdown intermediary. Package-visible and browser-free so it can be tested
     * without a browser driver. Roles come straight from the turns (already user /
     * assistant / unknown); a blank title falls back to a sensible default.
     */
    static ImportedChat toImportedChat(String title, List<ClaudeTurn> turns, String importedAt) {
        return toImportedChat(title, null, turns, importedAt);
    }

    static ImportedChat toImportedChat(String title, String sourceUri, List<ClaudeTurn> turns, String importedAt) {
        String chatTitle = (title == null || title.isBlank()) ? "Claude (web) live chat" : title.strip();
        Chat chat = new Chat(0, null, Source.claudeWeb, chatTitle, null, null, importedAt, false,
                new ImportMetadata(ProviderIdentity.claudeWebId(sourceUri), sourceUri, null, null, importedAt));

        List<Message> messages = new ArrayList<>();
        int sequence = 0;
        for (ClaudeTurn turn : turns) {
            messages.add(new Message(0, 0, turn.role(), turn.text(), sequence++, null, null));
        }
        return new ImportedChat(chat, messages);
    }

    static List<ConversationCandidate> candidates(List<CdpTranscriptAdapter.ChatWebSummary> summaries) {
        Map<String, ConversationCandidate> byIdentity = new LinkedHashMap<>();
        for (CdpTranscriptAdapter.ChatWebSummary summary : summaries) {
            String id = ProviderIdentity.claudeWebId(summary.url());
            String key = id == null ? summary.url() : id;
            byIdentity.putIfAbsent(key, new ConversationCandidate(
                    Source.claudeWeb, id, cleanTitle(summary.title(), "Claude (web) live chat"),
                    summary.url(), null));
        }
        return List.copyOf(byIdentity.values());
    }

    private static String cleanTitle(String title, String fallback) {
        return title == null || title.isBlank() ? fallback : title.strip();
    }
}
