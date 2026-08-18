package chatmap.infrastructure.provider.web;

import org.slf4j.Logger;
import chatmap.application.support.Log;


import chatmap.infrastructure.provider.ClaudeTurn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final Logger LOG = Log.of(CdpTranscriptAdapter.class);


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

    private static final int MAX_DISCOVERY_ROUNDS = 500;
    private static final int STABLE_ROUNDS_REQUIRED = 3;
    private static final long SCROLL_WAIT_MILLIS = 900;

    /**
     * Exhaustively discovers every conversation the current authenticated
     * session can enumerate, until a verified terminal condition is reached
     * (or the iteration budget/an error forces an honest incomplete/failed/
     * unavailable result instead). Self-contained like the other public
     * methods here: attaches, discovers, releases the CDP connection.
     */
    public final WebDiscoveryResult discoverAll() {
        lastUnavailableReason = null;
        try {
            if (!connectViaCdpOnly()) {
                return WebDiscoveryResult.unavailable(providerLabel(), "CDP connection could not be established");
            }
            WebDiscoveryResult result = doDiscoverAll();
            if (result.status() != DiscoveryStatus.complete) {
                lastUnavailableReason = result.reason();
            }
            return result;
        } catch (Exception failure) {
            String reason = diagnostic(failure);
            lastUnavailableReason = reason;
            return WebDiscoveryResult.failed(providerLabel(), reason);
        } finally {
            close();
        }
    }

    /** Display name used in discovery results; override if it should differ from the class name. */
    String providerLabel() {
        return getClass().getSimpleName();
    }

    /**
     * A durable identity for discovery-time deduplication, or null when the
     * summary carries no durable id yet (the caller falls back to the
     * summary's URL, which is still stable within one discovery run).
     * Override with the site's real identity extraction; the default treats
     * every summary as identity-less.
     */
    String identityOf(ChatWebSummary summary) {
        return null;
    }

    /** One scroll attempt: whether a scrollable container was found, and whether it actually moved. */
    record ScrollAttempt(boolean found, double before, double after) {
        boolean moved() {
            return found && after > before + 1;
        }

        static ScrollAttempt parse(String json) {
            try {
                JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                if (!o.has("found") || !o.get("found").getAsBoolean()) {
                    return new ScrollAttempt(false, 0, 0);
                }
                return new ScrollAttempt(true, o.get("before").getAsDouble(), o.get("after").getAsDouble());
            } catch (RuntimeException malformed) {
                return new ScrollAttempt(false, 0, 0);
            }
        }
    }

    /**
     * Scrolls the site's conversation list one step further, if a scrollable
     * container can be found. The default heuristic (the widest block-level
     * element whose content overflows its own box) was verified live against
     * claude.ai's and gemini.google.com's conversation lists on 2026-08-12;
     * override for a site whose scroll container it cannot find.
     */
    ScrollAttempt scrollForMore(CdpPage page) {
        Object raw = page.evaluate("() => {"
                + "const el=[...document.querySelectorAll('div')]"
                + ".filter(e => e.scrollHeight > e.clientHeight + 40"
                + " && e.clientHeight > 80 && e.clientHeight < 2000)"
                + ".sort((a,b) => b.scrollHeight - a.scrollHeight)[0];"
                + "if (!el) return JSON.stringify({found:false});"
                + "const before = el.scrollTop;"
                + "el.scrollTop = el.scrollHeight;"
                + "return JSON.stringify({found:true, before:before, after:el.scrollTop});"
                + "}");
        return ScrollAttempt.parse(raw == null ? "{\"found\":false}" : raw.toString());
    }

    /**
     * Default exhaustive strategy: repeatedly read the currently-rendered
     * conversation summaries via {@link #listChats}, accumulate by identity,
     * and scroll for more via {@link #scrollForMore}. Declares completion
     * only once the scroll position has genuinely stopped advancing for
     * several consecutive rounds — "no new items this round" alone is not
     * trusted, since a slow lazy load could still be in flight. Adapters
     * with a safer authenticated listing endpoint override this entirely
     * rather than depending on DOM scrolling at all.
     */
    WebDiscoveryResult doDiscoverAll() {
        CdpPage page;
        try {
            page = openPage(siteBaseUrl());
        } catch (Exception unopenable) {
            return WebDiscoveryResult.unavailable(providerLabel(), diagnostic(unopenable));
        }
        return accumulate(page);
    }

    /** How long to wait after a scroll for lazy-loaded entries; overridable so tests run instantly. */
    long scrollWaitMillis() {
        return SCROLL_WAIT_MILLIS;
    }

    /** Safety-net iteration cap; overridable so a "never stabilizes" test doesn't spin for real. */
    int maxDiscoveryRounds() {
        return MAX_DISCOVERY_ROUNDS;
    }

    /**
     * The scroll-and-accumulate loop itself, isolated from page-opening so it
     * can be driven directly in tests against a hand-built {@link CdpPage}.
     */
    final WebDiscoveryResult accumulate(CdpPage page) {
        LinkedHashMap<String, ChatWebSummary> byIdentity = new LinkedHashMap<>();
        int stableRounds = 0;
        int limit = maxDiscoveryRounds();
        for (int round = 0; round < limit; round++) {
            List<ChatWebSummary> batch;
            try {
                batch = listChats(page);
            } catch (Exception selectorFailure) {
                return new WebDiscoveryResult(providerLabel(), List.copyOf(byIdentity.values()),
                        DiscoveryStatus.incomplete,
                        "Selector failure while reading the conversation list: " + diagnostic(selectorFailure));
            }
            int before = byIdentity.size();
            for (ChatWebSummary summary : batch) {
                String id = identityOf(summary);
                byIdentity.putIfAbsent(id == null || id.isBlank() ? summary.url() : id, summary);
            }
            boolean grew = byIdentity.size() > before;

            ScrollAttempt attempt = scrollForMore(page);
            stableRounds = (grew || attempt.moved() || !attempt.found()) ? 0 : stableRounds + 1;

            if (stableRounds >= STABLE_ROUNDS_REQUIRED) {
                return new WebDiscoveryResult(providerLabel(), List.copyOf(byIdentity.values()),
                        DiscoveryStatus.complete,
                        "Complete for the normal conversation list exposed by the current authenticated web UI: "
                        + "scroll position and discovered count both stayed unchanged across "
                        + STABLE_ROUNDS_REQUIRED + " consecutive checks.");
            }
            page.waitForTimeout(scrollWaitMillis());
        }
        return new WebDiscoveryResult(providerLabel(), List.copyOf(byIdentity.values()),
                DiscoveryStatus.incomplete,
                "Reached the discovery iteration limit (" + limit
                + ") without a verified terminal condition.");
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
            LOG.debug("Silenced exception: {}", ignored.getMessage(), ignored);
        }
        }
        openPages.clear();
        browser = null;
    }

    static String diagnostic(Exception failure) {
        String simpleName = failure.getClass().getSimpleName();
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return simpleName;
        }
        return simpleName + ": " + message;
    }
    protected static com.google.gson.JsonObject parseOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return com.google.gson.JsonParser.parseString(raw.toString()).getAsJsonObject();
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    protected static String stringField(com.google.gson.JsonObject object, String name) {
        com.google.gson.JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

}

