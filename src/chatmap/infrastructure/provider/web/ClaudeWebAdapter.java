package chatmap.infrastructure.provider.web;

import org.slf4j.Logger;
import chatmap.application.support.Log;


import chatmap.infrastructure.provider.ClaudeTurn;
import chatmap.infrastructure.provider.ProviderIdentity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the most recent claude.ai conversation over CDP.
 *
 * Attaches to a Chrome already listening on the CDP port — the shared plumbing
 * (connect, open page, read newest, release) lives in {@link CdpTranscriptAdapter};
 * this class supplies only claude.ai's URL and selectors. It never launches a
 * browser itself ({@link ChromeCdpLauncher} does that).
 *
 * The selectors are best-effort and undocumented; claude.ai's DOM changes, so
     * {@link #readTurns(CdpPage)} tries several candidates and MUST be verified against a
 * live logged-in page if it stops finding turns.
 */
public final class ClaudeWebAdapter extends CdpTranscriptAdapter {

    private static final Logger LOG = Log.of(ClaudeWebAdapter.class);

    public static final String CLAUDE_BASE_URL = "https://claude.ai";

    ClaudeWebAdapter(String cdpUrl) {
        super(cdpUrl);
    }

    @Override
    String siteBaseUrl() {
        return CLAUDE_BASE_URL;
    }

    /**
     * Lists claude.ai conversations from the sidebar, most-recent first.
     * claude.ai conversation links are {@code /chat/<uuid>}; the sidebar renders
     * them newest-first, so element order is recency order.
     */
    @Override
    List<ChatWebSummary> listChats(CdpPage page) {
        Objects.requireNonNull(page, "page");
        try {
            page.waitForSelector("a[href*='/chat/']", 5000);
        } catch (Exception ignored) {
            LOG.debug("Silenced exception: {}", ignored.getMessage(), ignored);
        }

        List<ChatWebSummary> summaries = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        CdpPage.CdpLocator chatLinks = page.locator("a[href*='/chat/']");
        int count = chatLinks.count();
        for (int i = 0; i < count; i++) {
            CdpPage.CdpLocator link = chatLinks.nth(i);
            String href = link.getAttribute("href");
            if (href == null || href.isBlank()) {
                continue;
            }
            String fullUrl = href.startsWith("/") ? CLAUDE_BASE_URL + href : href;
            // The sidebar title is split into two identical spans (data-testid
            // 'chat-title-split'), so innerText repeats it — collapse the repeat.
            String title = WebTranscripts.collapseRepeatedLines(WebTranscripts.firstNonBlank(
                    WebTranscripts.safeInnerText(link), link.getAttribute("title"),
                    link.getAttribute("aria-label")));
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
     * Reads the turns of an open claude.ai conversation page, preserving
     * user/assistant roles. Tries several selectors in order and falls back; the
     * base class merges consecutive same-role turns into one message.
     *
     * NOTE: these selectors are best-effort and MUST be verified against a live
     * logged-in page; adjust the candidate lists below if claude.ai has changed.
     * The first candidate was verified against the live DOM on 2026-08-04: user
     * turns carry {@code data-testid='user-message'} and assistant response prose
     * lives in {@code font-claude-response-body} elements. Note the sibling
     * {@code font-claude-response} class holds Claude's collapsed *thinking*
     * summary, not the answer, so it is deliberately not selected. The later
     * candidates are older fallbacks.
     */
    @Override
    List<ClaudeTurn> readTurns(CdpPage page) {
        Objects.requireNonNull(page, "page");

        String[] turnSelectorCandidates = {
                "[data-testid='user-message'], .font-claude-response-body",
                "div[data-testid='user-message'], div[data-testid='assistant-message']",
                "[data-testid='user-message'], .font-claude-message",
                "div.font-user-message, div.font-claude-message",
        };

        try {
            page.waitForSelector(String.join(", ", turnSelectorCandidates), 5000);
        } catch (Exception ignored) {
            LOG.debug("Silenced exception: {}", ignored.getMessage(), ignored);
        }

        for (String selector : turnSelectorCandidates) {
            CdpPage.CdpLocator turns = page.locator(selector);
            int count = turns.count();
            if (count == 0) {
                continue;
            }
            List<ClaudeTurn> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                CdpPage.CdpLocator turn = turns.nth(i);
                String text = WebTranscripts.safeInnerText(turn);
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

    @Override
    String identityOf(ChatWebSummary summary) {
        return ProviderIdentity.claudeWebId(summary.url());
    }

    @Override
    String providerLabel() {
        return "Claude (web)";
    }

    private static final int PAGE_LIMIT = 30;
    private static final int MAX_API_PAGES = 200;

    /**
     * Exhaustive discovery via claude.ai's own {@code chat_conversations_v2}
     * listing endpoint instead of DOM scrolling: {@code has_more} in the
     * response is an explicit, authoritative terminal signal, and it sidesteps
     * a discrepancy actually observed live where the sidebar/{@code /recents}
     * DOM's {@code a[href*='/chat/']} count (title elements can render as more
     * than one anchor) exceeded the true conversation count. Called via
     * {@code fetch()} from the already-authenticated page context (cookies
     * only, same origin) -- no credential is read into or logged by this code.
     * Verified live on 2026-08-12: the endpoint has no separate archived-list
     * parameter, so there is nothing additional to enumerate there.
     */
    @Override
    WebDiscoveryResult doDiscoverAll() {
        CdpPage page;
        try {
            page = openPage(siteBaseUrl());
        } catch (Exception unopenable) {
            return WebDiscoveryResult.unavailable(providerLabel(), diagnostic(unopenable));
        }

        String orgId = extractOrgId(page);
        if (orgId == null || orgId.isBlank()) {
            return new WebDiscoveryResult(providerLabel(), List.of(), DiscoveryStatus.unavailable,
                    "Could not determine the active organization id (lastActiveOrg cookie missing); "
                    + "probably not logged in.");
        }

        return enumerate(orgId, offset -> page.evaluate(conversationPageScript(orgId, offset)), MAX_API_PAGES);
    }

    @FunctionalInterface
    interface ConversationPageFetcher {
        Object fetch(int offset);
    }

    static WebDiscoveryResult enumerate(
            String orgId, ConversationPageFetcher fetcher, int maxPages) {
        if (orgId == null || orgId.isBlank()) {
            return new WebDiscoveryResult("Claude (web)", List.of(), DiscoveryStatus.unavailable,
                    "Could not determine the active organization id (lastActiveOrg cookie missing); "
                    + "probably not logged in.");
        }

        Map<String, ChatWebSummary> all = new LinkedHashMap<>();
        int offset = 0;
        for (int i = 0; i < maxPages; i++) {
            Object raw = fetcher.fetch(offset);
            JsonObject parsed = parseOrNull(raw);
            if (parsed == null) {
                return new WebDiscoveryResult("Claude (web)", List.copyOf(all.values()), DiscoveryStatus.incomplete,
                        "Conversation listing API returned an unparseable response at offset " + offset + ".");
            }
            if (parsed.has("error")) {
                return new WebDiscoveryResult("Claude (web)", List.copyOf(all.values()), DiscoveryStatus.incomplete,
                        "Conversation listing API error at offset " + offset + ": "
                        + parsed.get("error").getAsString());
            }
            if (!parsed.has("data") || !parsed.get("data").isJsonArray()
                    || !parsed.has("has_more") || !parsed.get("has_more").isJsonPrimitive()
                    || !parsed.getAsJsonPrimitive("has_more").isBoolean()) {
                return new WebDiscoveryResult("Claude (web)", List.copyOf(all.values()), DiscoveryStatus.incomplete,
                        "Conversation listing API returned a malformed page at offset " + offset + ".");
            }
            JsonArray data = parsed.getAsJsonArray("data");
            for (JsonElement element : data) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject o = element.getAsJsonObject();
                String uuid = stringField(o, "uuid");
                if (uuid == null || uuid.isBlank()) {
                    continue;
                }
                String title = stringField(o, "name");
                all.putIfAbsent(uuid, new ChatWebSummary(
                        title == null || title.isBlank() ? "Untitled Chat" : title,
                        CLAUDE_BASE_URL + "/chat/" + uuid));
            }
            boolean hasMore = parsed.get("has_more").getAsBoolean();
            if (!hasMore) {
                return new WebDiscoveryResult("Claude (web)", List.copyOf(all.values()), DiscoveryStatus.complete,
                        "Complete for the normal conversation list exposed by the current authenticated web UI "
                        + "(chat_conversations_v2 has_more=false). No separate archived-conversation listing "
                        + "was found on this endpoint.");
            }
            offset += PAGE_LIMIT;
        }
        return new WebDiscoveryResult("Claude (web)", List.copyOf(all.values()), DiscoveryStatus.incomplete,
                "Reached the API pagination safety limit (" + maxPages + " pages) without has_more=false.");
    }

    private static String conversationPageScript(String orgId, int offset) {
        return "(async () => { try {"
                + "const res = await fetch('/api/organizations/" + orgId
                + "/chat_conversations_v2?limit=" + PAGE_LIMIT + "&offset=" + offset + "', "
                + "{credentials:'include'});"
                + "if (!res.ok) return JSON.stringify({error:'HTTP '+res.status,status:res.status});"
                + "return JSON.stringify(await res.json());"
                + "} catch (e) { return JSON.stringify({error:String(e)}); } })()";
    }

    private static String extractOrgId(CdpPage page) {
        Object raw = page.evaluate("() => { const m = document.cookie.match(/lastActiveOrg=([^;]+)/); "
                + "return m ? m[1] : null; }");
        return raw == null ? null : raw.toString();
    }

    private static JsonObject parseOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return JsonParser.parseString(raw.toString()).getAsJsonObject();
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static String stringField(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String inferRole(CdpPage.CdpLocator turn) {
        String testId = turn.getAttribute("data-testid");
        if (testId != null) {
            String lowered = testId.toLowerCase(Locale.ROOT);
            if (lowered.contains("user")) {
                return chatmap.domain.MessageRole.user.dbValue();
            }
            if (lowered.contains("assistant") || lowered.contains("claude")) {
                return chatmap.domain.MessageRole.assistant.dbValue();
            }
        }
        String classAttr = turn.getAttribute("class");
        if (classAttr != null) {
            String lowered = classAttr.toLowerCase(Locale.ROOT);
            if (lowered.contains("user")) {
                return chatmap.domain.MessageRole.user.dbValue();
            }
            if (lowered.contains("claude") || lowered.contains("assistant")) {
                return chatmap.domain.MessageRole.assistant.dbValue();
            }
        }
        return chatmap.domain.MessageRole.unknown.dbValue();
    }
}
