package chatmap.backend.web;

import chatmap.backend.providers.ClaudeTurn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Base for adapters that read a vendor's web chat over CDP from an already-running,
 * already-logged-in Chrome. Handles the shared plumbing — attach to CDP only (never
 * launch a browser here; {@link ChromeCdpLauncher} does that), find/open a page,
 * read the newest conversation, release the connection — and leaves the
 * site-specific selectors to subclasses.
 *
 * Subclasses provide the site's base URL, how to list conversations (most-recent
 * first), and how to read a conversation's turns. Selectors are inherently
 * best-effort and must be verified against the live site.
 */
abstract class CdpTranscriptAdapter implements AutoCloseable {

    /** A conversation's display title and its full URL. */
    record ChatWebSummary(String title, String url) {}

    /** The most recent conversation's title, URL, and turns, ready to import. */
    record Transcript(String title, String url, List<ClaudeTurn> turns) {
        Transcript {
            turns = List.copyOf(turns);
        }
    }

    private final String cdpUrl;
    private CdpBrowserConnection browser;
    private final List<CdpPage> openPages = new ArrayList<>();
    private String lastUnavailableReason;

    CdpTranscriptAdapter(String cdpUrl) {
        this.cdpUrl = Objects.requireNonNull(cdpUrl, "cdpUrl");
    }

    /** The site's entry URL, opened to read the conversation list (e.g. https://chatgpt.com). */
    abstract String siteBaseUrl();

    /** Conversations from the sidebar, most-recent first (titles + urls only). */
    abstract List<ChatWebSummary> listChats(CdpPage page);

    /** The turns of an open conversation page, in order (role + text). */
    abstract List<ClaudeTurn> readTurns(CdpPage page);

    /** A newest conversation that has been opened: its title and the loaded page. */
    record OpenConversation(String title, String url, CdpPage page) {}

    /**
     * Opens the newest conversation and returns its title + loaded page. The
     * default navigates by URL from {@link #listChats(CdpPage)}; sites whose sidebar
     * navigates via JS (no conversation URL to open) override this to click.
     */
    Optional<OpenConversation> openLatestConversation() {
        List<ChatWebSummary> chats = listChats(openPage(siteBaseUrl()));
        if (chats.isEmpty()) {
            return Optional.empty();
        }
        ChatWebSummary latest = chats.get(0);
        return openConversation(latest);
    }

    Optional<OpenConversation> openConversation(ChatWebSummary summary) {
        return Optional.of(new OpenConversation(summary.title(), summary.url(), openPage(summary.url())));
    }

    public final List<ChatWebSummary> discoverableChats() {
        lastUnavailableReason = null;
        try {
            if (!connectViaCdpOnly()) {
                return List.of();
            }
            return List.copyOf(listChats(openPage(siteBaseUrl())));
        } catch (Exception unavailable) {
            lastUnavailableReason = diagnostic(unavailable);
            return List.of();
        } finally {
            close();
        }
    }

    /**
     * The most recent conversation as title + merged turns, or empty when nothing
     * is reachable (browser not running, not logged in, no chats, or the layout no
     * longer matches). Never throws for the "nothing available" case. Self-contained:
     * attaches, reads, and releases the CDP connection each call.
     */
    public final Optional<Transcript> latestTranscript() {
        lastUnavailableReason = null;
        try {
            if (!connectViaCdpOnly()) {
                return Optional.empty();
            }
            Optional<OpenConversation> conversation = openLatestConversation();
            if (conversation.isEmpty()) {
                return Optional.empty();
            }
            List<ClaudeTurn> turns =
                    WebTranscripts.mergeConsecutiveSameRole(readTurns(conversation.get().page()));
            if (turns.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Transcript(conversation.get().title(), conversation.get().url(), turns));
        } catch (Exception unavailable) {
            lastUnavailableReason = diagnostic(unavailable);
            return Optional.empty();
        } finally {
            close(); // release the CDP connection (does not close the user's Chrome)
        }
    }

    public final Optional<Transcript> transcript(ChatWebSummary summary) {
        lastUnavailableReason = null;
        try {
            if (!connectViaCdpOnly()) {
                return Optional.empty();
            }
            Optional<OpenConversation> conversation = openConversation(summary);
            if (conversation.isEmpty()) {
                return Optional.empty();
            }
            List<ClaudeTurn> turns =
                    WebTranscripts.mergeConsecutiveSameRole(readTurns(conversation.get().page()));
            if (turns.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Transcript(conversation.get().title(), conversation.get().url(), turns));
        } catch (Exception unavailable) {
            lastUnavailableReason = diagnostic(unavailable);
            return Optional.empty();
        } finally {
            close();
        }
    }

    public final Optional<String> lastUnavailableReason() {
        return Optional.ofNullable(lastUnavailableReason);
    }

    /** Attaches to a running Chrome over CDP only; never launches a browser. */
    final boolean connectViaCdpOnly() {
        if (browser != null) {
            return true;
        }
        browser = new CdpBrowserConnection(cdpUrl);
        return true;
    }

    /** Finds an already-open page for the URL or opens a new one. Uses the CDP browser only. */
    final CdpPage openPage(String url) {
        try {
            CdpPage page = browser.openPage(url).orElseThrow();
            openPages.add(page);
            return page;
        } catch (Exception unavailable) {
            throw new IllegalStateException("Could not open CDP page", unavailable);
        }
    }

    @Override
    public synchronized void close() {
        for (CdpPage page : openPages) {
            try {
                page.close();
            } catch (Exception ignored) {
            }
        }
        openPages.clear();
        browser = null;
    }

    private static String diagnostic(Exception failure) {
        String simpleName = failure.getClass().getSimpleName();
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return simpleName;
        }
        return simpleName + ": " + message;
    }
}
