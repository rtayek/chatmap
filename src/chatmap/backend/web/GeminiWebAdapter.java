package chatmap.backend.web;

import chatmap.backend.providers.SessionLines;

import chatmap.backend.providers.ClaudeTurn;
import chatmap.backend.providers.ProviderIdentity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongConsumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads the most recent gemini.google.com conversation over CDP.
 *
 * Verified against the live logged-in page (2026-08-10). Gemini is an Angular app;
 * conversations are {@code [data-test-id="conversation"]} sidebar items, each
 * wrapping a single {@code <a href="/app/<id>">} with a durable per-conversation
 * URL. That URL is read directly off the anchor and navigated to — no click is
 * needed to discover or open a conversation. (An earlier version of this adapter
 * clicked the sidebar item itself to navigate, which doesn't work: {@code .click()}
 * on a wrapper element dispatches a click that bubbles up through ancestors, not
 * down into a child anchor, so the router never saw a real anchor click and the
 * page silently stayed on the zero-state "new chat" screen.) Once loaded, turns are
 * {@code <user-query>} and {@code <model-response>} custom elements; the clean text
 * is in {@code .query-text} (user) and {@code .markdown} (model) — the elements'
 * own innerText carries "You said…" / "Gemini said…" accessibility prefixes, which
 * reading the inner content elements avoids.
 */
public final class GeminiWebAdapter extends CdpTranscriptAdapter {

    static final String BASE_URL = "https://gemini.google.com/app";
    static final String SIDEBAR_URI_PREFIX = "gemini-sidebar-index:";
    private static final String CONVERSATION_SELECTOR = "[data-test-id='conversation']";
    private static final String CONVERSATION_LINK_SELECTOR = "[data-test-id='conversation'] a";
    private static final int STABLE_TERMINAL_ROUNDS = 3;
    private static final int MAX_GEMINI_DISCOVERY_ROUNDS = 40;
    private static final long GEMINI_SCROLL_WAIT_MILLIS = 1_000;

    GeminiWebAdapter(String cdpUrl) {
        super(cdpUrl);
    }

    @Override
    String siteBaseUrl() {
        return BASE_URL;
    }

    @Override
    List<ChatWebSummary> listChats(CdpPage page) {
        CdpPage.CdpLocator links = ensureSidebarExpanded(page);
        CdpPage.CdpLocator conversations = page.locator(CONVERSATION_SELECTOR);
        int count = links.count();
        List<ChatWebSummary> summaries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CdpPage.CdpLocator conversation = conversations.nth(i);
            String title = WebTranscripts.collapseRepeatedLines(WebTranscripts.safeInnerText(conversation));
            String uri = durableUrl(links.nth(i).getAttribute("href"));
            summaries.add(new ChatWebSummary(
                    title == null || title.isBlank() ? "Gemini conversation" : title,
                    uri == null ? SIDEBAR_URI_PREFIX + i : uri));
        }
        return summaries;
    }

    @Override
    Optional<OpenConversation> openLatestConversation() {
        CdpPage page = openPage(siteBaseUrl());
        try {
            CdpPage.CdpLocator links = ensureSidebarExpanded(page);
            if (links.count() == 0) {
                return Optional.empty();
            }
            CdpPage.CdpLocator newest = page.locator(CONVERSATION_SELECTOR).first();
            String title = WebTranscripts.collapseRepeatedLines(WebTranscripts.safeInnerText(newest));
            String url = durableUrl(links.first().getAttribute("href"));
            if (url != null) {
                page.navigate(url);
            } else {
                // No durable href found; fall back to clicking the anchor directly (not the
                // wrapper -- see the class doc for why that distinction matters).
                page.locator(CONVERSATION_LINK_SELECTOR).first().click(6000);
                page.waitForTimeout(3000);
            }
            return Optional.of(new OpenConversation(
                    title == null || title.isBlank() ? "Gemini conversation" : title, page.url(), page));
        } catch (Exception unavailable) {
            return Optional.empty();
        }
    }

    @Override
    Optional<OpenConversation> openConversation(ChatWebSummary summary) {
        if (summary.url().startsWith(SIDEBAR_URI_PREFIX)) {
            CdpPage page = openPage(siteBaseUrl());
            int index = Integer.parseInt(summary.url().substring(SIDEBAR_URI_PREFIX.length()));
            CdpPage.CdpLocator links = page.locator(CONVERSATION_LINK_SELECTOR);
            if (index >= links.count()) {
                return Optional.empty();
            }
            links.nth(index).click(6000);
            page.waitForTimeout(3000);
            return Optional.of(new OpenConversation(summary.title(), page.url(), page));
        }
        return super.openConversation(summary);
    }

    @Override
    String identityOf(ChatWebSummary summary) {
        return ProviderIdentity.geminiWebId(summary.url());
    }

    @Override
    String providerLabel() {
        return "Gemini (web)";
    }

    /**
     * Gemini's conversation list is driven by Google's undocumented, version-coupled
     * {@code batchexecute} RPC protocol rather than a stable REST-style endpoint.
     * Discovery therefore stays in the authenticated sidebar, but unlike the generic
     * fallback it positively identifies the nearest scrollable ancestor of a known
     * conversation row and requires repeated terminal-position observations.
     */
    @Override
    WebDiscoveryResult doDiscoverAll() {
        CdpPage page;
        try {
            page = openPage(siteBaseUrl());
        } catch (Exception unopenable) {
            return WebDiscoveryResult.unavailable(providerLabel(), diagnostic(unopenable));
        }
        try {
            ensureSidebarExpanded(page);
        } catch (RuntimeException ignored) {
            // The provider-specific probe below distinguishes login, explicit empty,
            // unhydrated, and selector-failure states more accurately than this
            // best-effort expansion helper can.
        }
        return discoverConversationList(
                () -> inspectConversationList(page),
                page::waitForTimeout,
                MAX_GEMINI_DISCOVERY_ROUNDS);
    }

    @FunctionalInterface
    interface ConversationListProbe {
        ConversationListState inspect();
    }

    record ConversationListState(
            List<ChatWebSummary> conversations,
            boolean authenticatedUi,
            boolean explicitEmptyState,
            boolean loginPage,
            boolean containerFound,
            boolean atTerminal,
            boolean moved) {

        ConversationListState {
            conversations = List.copyOf(conversations);
        }
    }

    static WebDiscoveryResult discoverConversationList(
            ConversationListProbe probe, LongConsumer sleeper, int maxRounds) {
        Map<String, ChatWebSummary> discovered = new LinkedHashMap<>();
        int stableTerminalRounds = 0;
        boolean authenticatedUiSeen = false;
        boolean containerSeen = false;

        for (int round = 0; round < maxRounds; round++) {
            ConversationListState state;
            try {
                state = probe.inspect();
            } catch (RuntimeException selectorFailure) {
                return new WebDiscoveryResult("Gemini (web)", List.copyOf(discovered.values()),
                        DiscoveryStatus.incomplete,
                        "Could not inspect the Gemini conversation sidebar: " + diagnostic(selectorFailure));
            }

            if (state.loginPage()) {
                return new WebDiscoveryResult("Gemini (web)", List.copyOf(discovered.values()),
                        DiscoveryStatus.unavailable,
                        "Gemini displayed a login page instead of the authenticated conversation sidebar.");
            }

            authenticatedUiSeen |= state.authenticatedUi();
            containerSeen |= state.containerFound();
            int before = discovered.size();
            for (ChatWebSummary summary : state.conversations()) {
                String id = ProviderIdentity.geminiWebId(summary.url());
                if (id != null && !id.isBlank()) {
                    discovered.putIfAbsent(id, summary);
                }
            }
            boolean grew = discovered.size() > before;

            if (state.authenticatedUi() && state.explicitEmptyState()
                    && state.conversations().isEmpty() && discovered.isEmpty()) {
                return new WebDiscoveryResult("Gemini (web)", List.of(), DiscoveryStatus.complete,
                        "Complete for the normal conversation list exposed by the authenticated Gemini sidebar: "
                        + "the provider's explicit empty state was present. Archived or hidden conversations are "
                        + "outside this verified scope.");
            }

            if (state.authenticatedUi() && state.containerFound()
                    && state.atTerminal() && !state.moved() && !grew) {
                stableTerminalRounds++;
            } else {
                stableTerminalRounds = 0;
            }

            if (stableTerminalRounds >= STABLE_TERMINAL_ROUNDS) {
                return new WebDiscoveryResult("Gemini (web)", List.copyOf(discovered.values()),
                        DiscoveryStatus.complete,
                        "Complete for the normal conversation list exposed by the authenticated Gemini sidebar: "
                        + "its nearest scrollable ancestor remained at the terminal position with no new durable "
                        + "conversation ids across " + STABLE_TERMINAL_ROUNDS + " checks. Archived or hidden "
                        + "conversations are outside this verified scope.");
            }
            sleeper.accept(GEMINI_SCROLL_WAIT_MILLIS);
        }

        String reason;
        if (!authenticatedUiSeen) {
            reason = "The authenticated Gemini conversation UI or an explicit empty state could not be verified; "
                    + "the sidebar may be logged out, unhydrated, or its selectors may have changed.";
        } else if (!containerSeen) {
            reason = "The Gemini conversation rows were found, but their conversation-list scroll container "
                    + "could not be identified.";
        } else {
            reason = "The Gemini conversation-list container did not remain at a verified terminal position "
                    + "before the discovery iteration limit (" + maxRounds + ").";
        }
        return new WebDiscoveryResult("Gemini (web)", List.copyOf(discovered.values()),
                DiscoveryStatus.incomplete, reason);
    }

    private static ConversationListState inspectConversationList(CdpPage page) {
        Object raw = page.evaluate("() => {"
                + "const rowSelector=" + CdpPage.jsString(CONVERSATION_SELECTOR) + ";"
                + "const rows=[...document.querySelectorAll(rowSelector)];"
                + "const login=location.hostname==='accounts.google.com'"
                + "||!!document.querySelector('a[href*=\"accounts.google.com/ServiceLogin\"]');"
                + "const empty=!!document.querySelector("
                + "'[data-test-id=\"conversation-list-empty-state\"],'"
                + "+'[data-test-id=\"empty-conversation-list\"]');"
                + "let container=null;"
                + "if(rows.length){for(let p=rows[0].parentElement;p;p=p.parentElement){"
                + "const y=getComputedStyle(p).overflowY;"
                + "if((y==='auto'||y==='scroll'||y==='overlay')&&p.clientHeight>0){container=p;break;}}}"
                + "let before=0,after=0,terminal=false;"
                + "if(container){before=container.scrollTop;const max=Math.max(0,container.scrollHeight-container.clientHeight);"
                + "terminal=before>=max-2;container.scrollTop=max;after=container.scrollTop;}"
                + "const chats=rows.map((row,index)=>{const a=row.querySelector('a[href]');"
                + "return {title:(row.innerText||'').trim(),href:a?a.href:null,index:index};});"
                + "return JSON.stringify({chats:chats,authenticated:rows.length>0||empty,empty:empty,login:login,"
                + "container:!!container,terminal:terminal,moved:after>before+1});"
                + "}");
        return parseConversationListState(raw == null ? "{}" : raw.toString());
    }

    private static ConversationListState parseConversationListState(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<ChatWebSummary> chats = new ArrayList<>();
            if (root.has("chats") && root.get("chats").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("chats")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject chat = element.getAsJsonObject();
                    String href = SessionLines.string(chat, "href");
                    String url = durableUrl(href);
                    if (url == null) {
                        continue;
                    }
                    String title = SessionLines.string(chat, "title");
                    chats.add(new ChatWebSummary(
                            title == null || title.isBlank() ? "Gemini conversation" : title, url));
                }
            }
            return new ConversationListState(chats,
                    booleanField(root, "authenticated"), booleanField(root, "empty"),
                    booleanField(root, "login"), booleanField(root, "container"),
                    booleanField(root, "terminal"), booleanField(root, "moved"));
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("Malformed Gemini conversation-list probe response", malformed);
        }
    }

    private static boolean booleanField(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonPrimitive()
                && object.getAsJsonPrimitive(name).isBoolean()
                && object.get(name).getAsBoolean();
    }

    /** The absolute, durable conversation URL for a sidebar anchor's {@code href}, or null. */
    private static String durableUrl(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String url = href.startsWith("http") ? href : "https://gemini.google.com" + href;
        return ProviderIdentity.geminiWebId(url) == null ? null : url;
    }

    @Override
    List<ClaudeTurn> readTurns(CdpPage page) {
        Objects.requireNonNull(page, "page");
        try {
            page.waitForSelector("user-query, model-response", 5000);
        } catch (Exception ignored) {
            // No messages in time -> empty transcript.
        }

        // Read each turn's clean content element (avoids the a11y "You said"/"Gemini said" prefix).
        Object raw = page.evaluate("() => {"
                + "const out=[];"
                + "document.querySelectorAll('user-query, model-response').forEach(n=>{"
                + "const isUser=n.tagName.toLowerCase()==='user-query';"
                + "const c=isUser?(n.querySelector('.query-text')||n)"
                + ":(n.querySelector('.markdown, message-content')||n);"
                // Strip Gemini's screen-reader "You said" / "Gemini said" turn prefix.
                + "const text=(c.innerText||'').trim().replace(/^(You said|Gemini said)\\s+/i,'');"
                + "out.push({role:isUser?'user':'assistant',text});});"
                + "return JSON.stringify(out);}");
        return parseTurnsJson(raw == null ? "[]" : raw.toString());
    }

    /** Parses a {@code [{role,text},...]} JSON array into turns. Browser-free, so testable. */
    static List<ClaudeTurn> parseTurnsJson(String json) {
        List<ClaudeTurn> turns = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
                return turns;
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject o = element.getAsJsonObject();
                String role = SessionLines.string(o, "role");
                String text = SessionLines.string(o, "text");
                if (text == null || text.isBlank()) {
                    continue;
                }
                if (!chatmap.domain.MessageRole.user.dbValue().equals(role)
                        && !chatmap.domain.MessageRole.assistant.dbValue().equals(role)) {
                    continue;
                }
                turns.add(new ClaudeTurn(role, text.strip()));
            }
        } catch (RuntimeException ignored) {
            // return whatever parsed so far (empty on malformed input)
        }
        return turns;
    }

    /**
     * Returns a locator for the sidebar rows' anchors, expanding the sidebar first if needed.
     *
     * The {@code [data-test-id="conversation"]} wrappers render as soon as the list itself
     * mounts, but when the side nav is collapsed to its icon rail (aria-label "Open sidebar",
     * verified live) each row is just Angular comment placeholders with no {@code <a href>}
     * inside -- the wrapper count alone is not a reliable "ready" signal. Clicking the nav's
     * menu button expands it and populates the anchors.
     */
    private static CdpPage.CdpLocator ensureSidebarExpanded(CdpPage page) {
        CdpPage.CdpLocator links = page.locator(CONVERSATION_LINK_SELECTOR);
        if (links.count() > 0) {
            return links;
        }
        page.locator("chat-app-side-nav-menu-button button, button[aria-label*='menu' i]")
                .first().click(4000);
        links = awaitLinksHydrated(page);
        if (links.count() > 0) {
            return links;
        }
        // CdpBrowserConnection.openPage() reuses any already-open tab whose URL contains ours --
        // for a bare "/app" base URL that's *any* open conversation tab -- and skips navigate()
        // on reuse. A tab left mid-glitch (observed live: Gemini's own sidebar showing "Couldn't
        // connect" after a background cookie-rotation) then never gets a fresh load and sits
        // stuck. Force one here as a last resort.
        page.navigate(BASE_URL);
        return awaitLinksHydrated(page);
    }

    /** Polls for the sidebar anchors to populate (bounded), rather than a fixed blind sleep. */
    private static CdpPage.CdpLocator awaitLinksHydrated(CdpPage page) {
        CdpPage.CdpLocator links = page.locator(CONVERSATION_LINK_SELECTOR);
        long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
        while (System.nanoTime() < deadline) {
            if (links.count() > 0) {
                return links;
            }
            page.waitForTimeout(150);
            links = page.locator(CONVERSATION_LINK_SELECTOR);
        }
        return links;
    }
}
