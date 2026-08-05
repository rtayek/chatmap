package chatmap.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Reads claude.ai conversations from an already-running, already-logged-in
 * Chrome over the Chrome DevTools Protocol.
 *
 * Ported from MyClaw's PlaywrightWebAdapter (Claude-only parts). There is
 * exactly one way to get a browser here: attach to a Chrome already listening on
 * the CDP port. It never launches a browser itself — {@link ChromeCdpLauncher}
 * is responsible for making sure one is up before any of this runs. (The old
 * ChatGPT path's launch-a-fresh-headless-browser fallback is deliberately not
 * ported: a profileless, logged-out browser is useless for reading the session.)
 *
 * Its selectors are best-effort and undocumented; claude.ai's DOM changes, so
 * {@link #readClaudeTranscript(Page)} tries several candidates and MUST be
 * verified against a live logged-in page if it stops finding turns.
 */
public final class ClaudeWebAdapter implements AutoCloseable {

    public static final String CLAUDE_BASE_URL = "https://claude.ai";

    /** A conversation's display title and its full URL. */
    public record ChatWebSummary(String title, String url) {}

    /** The most recent conversation's title plus its turns, ready to import. */
    public record Transcript(String title, List<ClaudeTurn> turns) {
        public Transcript {
            turns = List.copyOf(turns);
        }
    }

    private final String cdpUrl;
    private Playwright playwright;
    private Browser browser;
    private boolean connectedViaCdp;

    public ClaudeWebAdapter(String cdpUrl) {
        this.cdpUrl = Objects.requireNonNull(cdpUrl, "cdpUrl");
    }

    /**
     * The most recent claude.ai conversation as title + turns, or empty when
     * nothing is reachable (browser not running, not logged in, no chats, or the
     * page layout no longer matches any known selector). Never throws for the
     * "nothing available" case. Self-contained: it attaches, reads, and releases
     * the Playwright connection each call, so a one-shot CLI leaves nothing behind.
     */
    public Optional<Transcript> latestTranscript() {
        try {
            if (!connectViaCdpOnly()) {
                return Optional.empty();
            }
            Optional<ChatWebSummary> latest = latestClaudeChat();
            if (latest.isEmpty()) {
                return Optional.empty();
            }
            Page page = openConversation(latest.get().url());
            List<ClaudeTurn> turns = readClaudeTranscript(page);
            if (turns.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Transcript(latest.get().title(), turns));
        } catch (Exception unavailable) {
            return Optional.empty();
        } finally {
            close(); // release the CDP connection (does not close the user's Chrome)
        }
    }

    /**
     * Attaches to an already-running Chrome over CDP only; never launches a
     * browser. Returns false (leaving the adapter unconnected) when no CDP
     * endpoint is reachable.
     */
    boolean connectViaCdpOnly() {
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

    /** The most recently updated claude.ai conversation from the sidebar, or empty. */
    Optional<ChatWebSummary> latestClaudeChat() {
        List<ChatWebSummary> chats = listClaudeChats();
        return chats.isEmpty() ? Optional.empty() : Optional.of(chats.get(0));
    }

    /**
     * Lists claude.ai conversations from the sidebar, most-recent first.
     * claude.ai conversation links are {@code /chat/<uuid>}; the sidebar renders
     * them newest-first, so element order is recency order.
     */
    List<ChatWebSummary> listClaudeChats() {
        Page page = openConversation(CLAUDE_BASE_URL);
        Objects.requireNonNull(page, "page");
        try {
            page.waitForSelector("a[href*='/chat/']",
                    new Page.WaitForSelectorOptions().setTimeout(5000));
        } catch (Exception ignored) {
            // Sidebar may be slow, or absent when logged out.
        }

        List<ChatWebSummary> summaries = new ArrayList<>();
        java.util.Set<String> seenUrls = new java.util.LinkedHashSet<>();
        Locator chatLinks = page.locator("a[href*='/chat/']");
        int count = chatLinks.count();
        for (int i = 0; i < count; i++) {
            Locator link = chatLinks.nth(i);
            String href = link.getAttribute("href");
            if (href == null || href.isBlank()) {
                continue;
            }
            String fullUrl = href.startsWith("/") ? CLAUDE_BASE_URL + href : href;
            String title = firstNonBlank(
                    safeInnerText(link), link.getAttribute("title"), link.getAttribute("aria-label"));
            if (title == null || title.isBlank()) {
                title = "Untitled Chat";
            }
            if (seenUrls.add(fullUrl)) {
                summaries.add(new ChatWebSummary(title.strip(), fullUrl));
            }
        }
        return summaries;
    }

    /**
     * Reads the full transcript of an open claude.ai conversation page, turn by
     * turn, preserving user/assistant roles. Tries several selectors in order and
     * falls back; roles are inferred from stable-looking attributes first.
     *
     * NOTE: these selectors are best-effort and MUST be verified against a live
     * logged-in page; adjust the candidate lists below if claude.ai has changed.
     */
    List<ClaudeTurn> readClaudeTranscript(Page page) {
        Objects.requireNonNull(page, "page");

        String[] turnSelectorCandidates = {
                "div[data-testid='user-message'], div[data-testid='assistant-message']",
                "[data-testid='user-message'], .font-claude-message",
                "div.font-user-message, div.font-claude-message",
        };

        try {
            page.waitForSelector(String.join(", ", turnSelectorCandidates),
                    new Page.WaitForSelectorOptions().setTimeout(5000));
        } catch (Exception ignored) {
            // No messages found in time (e.g. not logged in) -> empty transcript.
        }

        for (String selector : turnSelectorCandidates) {
            Locator turns = page.locator(selector);
            int count = turns.count();
            if (count == 0) {
                continue;
            }
            List<ClaudeTurn> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Locator turn = turns.nth(i);
                String text = safeInnerText(turn);
                if (text == null || text.isBlank()) {
                    continue;
                }
                result.add(new ClaudeTurn(inferRole(turn), text.strip()));
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return List.of();
    }

    /** Finds an already-open page for the URL or opens a new one. Uses the CDP browser only. */
    private Page openConversation(String url) {
        BrowserContext context = browser.contexts().isEmpty()
                ? browser.newContext()
                : browser.contexts().get(0);

        Optional<Page> existing = context.pages().stream()
                .filter(page -> page.url().contains(url) || url.contains(page.url()))
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

    private static String inferRole(Locator turn) {
        String testId = turn.getAttribute("data-testid");
        if (testId != null) {
            String lowered = testId.toLowerCase(Locale.ROOT);
            if (lowered.contains("user")) {
                return "user";
            }
            if (lowered.contains("assistant") || lowered.contains("claude")) {
                return "assistant";
            }
        }
        String classAttr = turn.getAttribute("class");
        if (classAttr != null) {
            String lowered = classAttr.toLowerCase(Locale.ROOT);
            if (lowered.contains("user")) {
                return "user";
            }
            if (lowered.contains("claude") || lowered.contains("assistant")) {
                return "assistant";
            }
        }
        return "unknown";
    }

    private static String safeInnerText(Locator locator) {
        try {
            return locator.innerText();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
