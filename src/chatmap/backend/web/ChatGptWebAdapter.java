package chatmap.backend.web;

import chatmap.backend.providers.ClaudeTurn;
import chatmap.backend.providers.ProviderIdentity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the most recent chatgpt.com conversation over CDP.
 *
 * Conversation links in the sidebar are {@code /c/<id>} (mirroring MyClaw's
 * listChatGPTChats), newest-first. Message turns carry a
 * {@code data-message-author-role} attribute ("user" / "assistant" / "system" /
 * "tool"); only user and assistant turns are kept — system/tool turns are not
 * conversation content.
 *
 * Verified against the live logged-in page (2026-08-04): the sidebar
 * {@code a[href*='/c/']} links and the {@code data-message-author-role} turn
 * markers both matched and read a real conversation correctly.
 */
public final class ChatGptWebAdapter extends CdpTranscriptAdapter {

    static final String BASE_URL = "https://chatgpt.com";

    ChatGptWebAdapter(String cdpUrl) {
        super(cdpUrl);
    }

    @Override
    String siteBaseUrl() {
        return BASE_URL;
    }

    @Override
    List<ChatWebSummary> listChats(CdpPage page) {
        Objects.requireNonNull(page, "page");
        try {
            page.waitForSelector("a[href*='/c/']", 5000);
        } catch (Exception ignored) {
            // Sidebar may be slow, or absent when logged out.
        }

        List<ChatWebSummary> summaries = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        CdpPage.CdpLocator links = page.locator("a[href*='/c/']");
        int count = links.count();
        for (int i = 0; i < count; i++) {
            CdpPage.CdpLocator link = links.nth(i);
            String href = link.getAttribute("href");
            if (href == null || href.isBlank()) {
                continue;
            }
            String fullUrl = href.startsWith("/") ? BASE_URL + href : href;
            String title = WebTranscripts.collapseRepeatedLines(WebTranscripts.firstNonBlank(
                    WebTranscripts.safeInnerText(link), link.getAttribute("title"),
                    link.getAttribute("aria-label")));
            if (title == null || title.isBlank()) {
                title = "Untitled Chat";
            }
            if (seenUrls.add(fullUrl)) {
                summaries.add(new ChatWebSummary(title, fullUrl));
            }
        }
        return summaries;
    }

    @Override
    List<ClaudeTurn> readTurns(CdpPage page) {
        Objects.requireNonNull(page, "page");
        try {
            page.waitForSelector("[data-message-author-role]", 5000);
        } catch (Exception ignored) {
            // No messages in time (e.g. not logged in) -> empty transcript.
        }

        List<ClaudeTurn> turns = new ArrayList<>();
        CdpPage.CdpLocator messages = page.locator("[data-message-author-role]");
        int count = messages.count();
        for (int i = 0; i < count; i++) {
            CdpPage.CdpLocator message = messages.nth(i);
            String role = mapRole(message.getAttribute("data-message-author-role"));
            if (role == null) {
                continue;
            }
            String text = WebTranscripts.safeInnerText(message);
            if (text != null && !text.isBlank()) {
                turns.add(new ClaudeTurn(role, text.strip()));
            }
        }
        return turns;
    }

    /** Maps ChatGPT's author-role attribute to a conversation role, or null to skip. */
    static String mapRole(String authorRole) {
        if (authorRole == null) {
            return null;
        }
        return switch (authorRole.toLowerCase(java.util.Locale.ROOT)) {
            case "user" -> chatmap.domain.MessageRole.user.dbValue();
            case "assistant" -> chatmap.domain.MessageRole.assistant.dbValue();
            default -> null; // Ignore system / tool roles
        };
    }

    @Override
    String identityOf(ChatWebSummary summary) {
        return ProviderIdentity.chatGptWebId(summary.url());
    }

    @Override
    String providerLabel() {
        return "ChatGPT (web)";
    }

    private static final int PAGE_LIMIT = 28;
    private static final int MAX_API_PAGES = 400;
    private static final long PAGE_DELAY_MILLIS = 500;

    /**
     * Exhaustive discovery via chatgpt.com's own {@code /backend-api/conversations}
     * listing endpoint. Unlike claude.ai, cookies alone are not sufficient here
     * (verified live: an unauthenticated-looking request returns HTTP 200 with an
     * empty list rather than an error) -- a bearer token from
     * {@code /api/auth/session} is required. The token is read and used entirely
     * within this single page-context {@code fetch()} call and never leaves the
     * browser or gets logged/returned to Java. The response's own {@code total}
     * field was verified live to NOT be the true grand total (it tracks
     * {@code offset + page length}, not an actual count) so the real terminal
     * signal used here is {@code items.length < limit}. Archived conversations
     * are enumerated with a second, separate paged pass ({@code is_archived=true})
     * since the site supports querying them explicitly.
     */
    @Override
    WebDiscoveryResult doDiscoverAll() {
        CdpPage page;
        try {
            page = openPage(siteBaseUrl());
        } catch (Exception unopenable) {
            return WebDiscoveryResult.unavailable(providerLabel(), diagnostic(unopenable));
        }

        List<ChatWebSummary> all = new ArrayList<>();
        PageResult activeResult = pageThrough(page, all, false);
        if (activeResult.status() != DiscoveryStatus.complete) {
            return new WebDiscoveryResult(providerLabel(), List.copyOf(all), activeResult.status(),
                    activeResult.reason());
        }
        int normalCount = all.size();
        PageResult archivedResult = pageThrough(page, all, true);
        if (archivedResult.status() != DiscoveryStatus.complete) {
            return new WebDiscoveryResult(providerLabel(), List.copyOf(all), archivedResult.status(),
                    "Normal conversation list complete (" + normalCount + "), but the archived list did not "
                    + "reach a verified terminal condition: " + archivedResult.reason());
        }
        int archivedCount = all.size() - normalCount;
        return new WebDiscoveryResult(providerLabel(), List.copyOf(all), DiscoveryStatus.complete,
                "Complete for the normal and archived conversation lists exposed by the current authenticated "
                + "web UI (" + normalCount + " normal + " + archivedCount + " archived, "
                + "terminal condition: a page shorter than the requested limit).");
    }

    private record PageResult(DiscoveryStatus status, String reason) {
    }

    /**
     * Pages through one conversation list (active or archived). A short delay
     * before every request after the first spaces out the burst of same-origin
     * fetches this makes -- observed live on 2026-08-12: back-to-back full-speed
     * pagination reliably triggered an HTTP 429 from chatgpt.com's backend
     * partway through a ~310-conversation account on a second run shortly after
     * a first. This must never regress to a false "complete": a 429 is still
     * reported as incomplete with the exact offset and status, same as any
     * other listing-API error.
     */
    private PageResult pageThrough(CdpPage page, List<ChatWebSummary> out, boolean archived) {
        int offset = 0;
        for (int i = 0; i < MAX_API_PAGES; i++) {
            if (i > 0) {
                page.waitForTimeout(PAGE_DELAY_MILLIS);
            }
            Object raw = page.evaluate("(async () => { try {"
                    + "const s = await fetch('/api/auth/session', {credentials:'include'}).then(r=>r.json());"
                    + "const tok = s && s.accessToken;"
                    + "if (!tok) return JSON.stringify({error:'no-access-token'});"
                    + "const res = await fetch('/backend-api/conversations?offset=" + offset + "&limit="
                    + PAGE_LIMIT + "&order=updated&is_archived=" + archived + "', "
                    + "{credentials:'include', headers:{Authorization:'Bearer '+tok}});"
                    + "if (!res.ok) return JSON.stringify({error:'HTTP '+res.status});"
                    + "return JSON.stringify(await res.json());"
                    + "} catch (e) { return JSON.stringify({error:String(e)}); } })()");
            JsonObject parsed = parseOrNull(raw);
            if (parsed == null) {
                return new PageResult(DiscoveryStatus.incomplete,
                        "Conversation listing API returned an unparseable response at offset " + offset + ".");
            }
            if (parsed.has("error")) {
                return new PageResult(DiscoveryStatus.incomplete,
                        "Conversation listing API error at offset " + offset + ": "
                        + parsed.get("error").getAsString());
            }
            JsonArray items = parsed.has("items") ? parsed.getAsJsonArray("items") : new JsonArray();
            for (JsonElement element : items) {
                JsonObject o = element.getAsJsonObject();
                String id = stringField(o, "id");
                if (id == null || id.isBlank()) {
                    continue;
                }
                String title = stringField(o, "title");
                out.add(new ChatWebSummary(title == null || title.isBlank() ? "Untitled Chat" : title,
                        BASE_URL + "/c/" + id));
            }
            if (items.size() < PAGE_LIMIT) {
                return new PageResult(DiscoveryStatus.complete, null);
            }
            offset += PAGE_LIMIT;
        }
        return new PageResult(DiscoveryStatus.incomplete,
                "Reached the API pagination safety limit (" + MAX_API_PAGES + " pages).");
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
}
