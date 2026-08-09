package chatmap.backend;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

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
    private Playwright playwright;
    private Browser browser;
    private boolean connectedViaCdp;

    CdpTranscriptAdapter(String cdpUrl) {
        this.cdpUrl = Objects.requireNonNull(cdpUrl, "cdpUrl");
    }

    /** The site's entry URL, opened to read the conversation list (e.g. https://chatgpt.com). */
    abstract String siteBaseUrl();

    /** Conversations from the sidebar, most-recent first (titles + urls only). */
    abstract List<ChatWebSummary> listChats(Page page);

    /** The turns of an open conversation page, in order (role + text). */
    abstract List<ClaudeTurn> readTurns(Page page);

    /** A newest conversation that has been opened: its title and the loaded page. */
    record OpenConversation(String title, String url, Page page) {}

    /**
     * Opens the newest conversation and returns its title + loaded page. The
     * default navigates by URL from {@link #listChats(Page)}; sites whose sidebar
     * navigates via JS (no conversation URL to open) override this to click.
     */
    Optional<OpenConversation> openLatestConversation() {
        List<ChatWebSummary> chats = listChats(openPage(siteBaseUrl()));
        if (chats.isEmpty()) {
            return Optional.empty();
        }
        ChatWebSummary latest = chats.get(0);
        return Optional.of(new OpenConversation(latest.title(), latest.url(), openPage(latest.url())));
    }

    /**
     * The most recent conversation as title + merged turns, or empty when nothing
     * is reachable (browser not running, not logged in, no chats, or the layout no
     * longer matches). Never throws for the "nothing available" case. Self-contained:
     * attaches, reads, and releases the CDP connection each call.
     */
    public final Optional<Transcript> latestTranscript() {
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
            return Optional.empty();
        } finally {
            close(); // release the CDP connection (does not close the user's Chrome)
        }
    }

    /** Attaches to a running Chrome over CDP only; never launches a browser. */
    final boolean connectViaCdpOnly() {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        if (browser != null && browser.isConnected() && connectedViaCdp) {
            return true;
        }
        try {
            browser = playwright.chromium().connectOverCDP(cdpUrl);
            connectedViaCdp = true;
            return true;
        } catch (Exception cdpUnreachable) {
            connectedViaCdp = false;
            return false;
        }
    }

    /** Finds an already-open page for the URL or opens a new one. Uses the CDP browser only. */
    final Page openPage(String url) {
        BrowserContext context = browser.contexts().isEmpty()
                ? browser.newContext()
                : browser.contexts().get(0);

        // Reuse a tab only if it is already AT (or deeper than) the target url. The
        // reverse match (target contains page url) is deliberately dropped: it made
        // opening ".../c/<id>" wrongly reuse a "chatgpt.com/" home tab (a prefix)
        // and never navigate to the conversation.
        Optional<Page> existing = context.pages().stream()
                .filter(page -> page.url().contains(url))
                .findFirst();
        if (existing.isPresent()) {
            Page page = existing.get();
            page.bringToFront();
            return page;
        }

        Page page = context.newPage();
        page.navigate(url);
        return page;
    }

    @Override
    public synchronized void close() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception ignored) {
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
            }
            playwright = null;
        }
        connectedViaCdp = false;
    }
}
