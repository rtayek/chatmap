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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;

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
    private static final Logger LOG = Log.of(ChatGptWebAdapter.class);


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
            LOG.debug("Silenced exception: {}", ignored.getMessage(), ignored);
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
            LOG.debug("Silenced exception: {}", ignored.getMessage(), ignored);
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
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final long MAX_RETRY_DELAY_MILLIS = 30_000;

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

        return enumerate(
                (offset, archived) -> page.evaluate(conversationPageScript(offset, archived)),
                page::waitForTimeout,
                MAX_API_PAGES,
                MAX_RATE_LIMIT_RETRIES);
    }

    @FunctionalInterface
    interface ConversationPageFetcher {
        Object fetch(int offset, boolean archived);
    }

    static WebDiscoveryResult enumerate(
            ConversationPageFetcher fetcher,
            LongConsumer sleeper,
            int maxPages,
            int maxRateLimitRetries) {
        Map<String, ChatWebSummary> all = new LinkedHashMap<>();
        PageResult activeResult = pageThrough(
                fetcher, sleeper, all, false, maxPages, maxRateLimitRetries);
        if (activeResult.status() != DiscoveryStatus.complete) {
            return new WebDiscoveryResult("ChatGPT (web)", List.copyOf(all.values()), activeResult.status(),
                    activeResult.reason());
        }
        int normalCount = activeResult.added();
        PageResult archivedResult = pageThrough(
                fetcher, sleeper, all, true, maxPages, maxRateLimitRetries);
        if (archivedResult.status() != DiscoveryStatus.complete) {
            return new WebDiscoveryResult("ChatGPT (web)", List.copyOf(all.values()), archivedResult.status(),
                    "Normal conversation list complete (" + normalCount + "), but the archived list did not "
                    + "reach a verified terminal condition: " + archivedResult.reason());
        }
        int archivedCount = archivedResult.added();
        int rateLimitRetries = activeResult.rateLimitRetries() + archivedResult.rateLimitRetries();
        return new WebDiscoveryResult("ChatGPT (web)", List.copyOf(all.values()), DiscoveryStatus.complete,
                "Complete for the normal and archived conversation lists exposed by the current authenticated "
                + "web UI (" + normalCount + " normal + " + archivedCount + " archived, "
                + "terminal condition: a page shorter than the requested limit)."
                + (rateLimitRetries == 0 ? "" : " Recovered from " + rateLimitRetries
                        + " HTTP 429 rate-limit response(s) with bounded retries."));
    }

    private record PageResult(DiscoveryStatus status, String reason, int added, int rateLimitRetries) {
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
    private static PageResult pageThrough(
            ConversationPageFetcher fetcher,
            LongConsumer sleeper,
            Map<String, ChatWebSummary> out,
            boolean archived,
            int maxPages,
            int maxRateLimitRetries) {
        int offset = 0;
        int added = 0;
        int rateLimitRetries = 0;
        for (int i = 0; i < maxPages; i++) {
            if (i > 0) {
                sleeper.accept(PAGE_DELAY_MILLIS);
            }
            JsonObject parsed = null;
            for (int retry = 0; retry <= maxRateLimitRetries; retry++) {
                parsed = parseOrNull(fetcher.fetch(offset, archived));
                if (parsed == null || responseStatus(parsed) != 429) {
                    break;
                }
                if (retry == maxRateLimitRetries) {
                    return new PageResult(DiscoveryStatus.incomplete,
                            "Conversation listing API exhausted " + maxRateLimitRetries
                            + " retries after HTTP 429 at offset " + offset + ".",
                            added, rateLimitRetries);
                }
                rateLimitRetries++;
                sleeper.accept(retryDelayMillis(parsed, retry));
            }
            if (parsed == null) {
                return new PageResult(DiscoveryStatus.incomplete,
                        "Conversation listing API returned an unparseable response at offset " + offset + ".",
                        added, rateLimitRetries);
            }
            if (parsed.has("error")) {
                return new PageResult(DiscoveryStatus.incomplete,
                        "Conversation listing API error at offset " + offset + ": "
                        + parsed.get("error").getAsString(), added, rateLimitRetries);
            }
            if (!parsed.has("items") || !parsed.get("items").isJsonArray()) {
                return new PageResult(DiscoveryStatus.incomplete,
                        "Conversation listing API returned a malformed page at offset " + offset + ".",
                        added, rateLimitRetries);
            }
            JsonArray items = parsed.getAsJsonArray("items");
            for (JsonElement element : items) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject o = element.getAsJsonObject();
                String id = stringField(o, "id");
                if (id == null || id.isBlank()) {
                    continue;
                }
                String title = stringField(o, "title");
                ChatWebSummary previous = out.putIfAbsent(id, new ChatWebSummary(
                        title == null || title.isBlank() ? "Untitled Chat" : title,
                        BASE_URL + "/c/" + id));
                if (previous == null) {
                    added++;
                }
            }
            if (items.size() < PAGE_LIMIT) {
                return new PageResult(DiscoveryStatus.complete, null, added, rateLimitRetries);
            }
            offset += PAGE_LIMIT;
        }
        return new PageResult(DiscoveryStatus.incomplete,
                "Reached the API pagination safety limit (" + maxPages + " pages).",
                added, rateLimitRetries);
    }

    private static String conversationPageScript(int offset, boolean archived) {
        return "(async () => { try {"
                + "let tok=globalThis.__chatmapAccessToken;"
                + "if(!tok){const s=await fetch('/api/auth/session',{credentials:'include'}).then(r=>r.json());"
                + "tok=s&&s.accessToken;if(tok)globalThis.__chatmapAccessToken=tok;}"
                + "if (!tok) return JSON.stringify({error:'no-access-token'});"
                + "const res = await fetch('/backend-api/conversations?offset=" + offset + "&limit="
                + PAGE_LIMIT + "&order=updated&is_archived=" + archived + "', "
                + "{credentials:'include', headers:{Authorization:'Bearer '+tok}});"
                + "if (!res.ok) return JSON.stringify({error:'HTTP '+res.status,status:res.status,"
                + "retryAfter:res.headers.get('Retry-After')});"
                + "return JSON.stringify(await res.json());"
                + "} catch (e) { return JSON.stringify({error:String(e)}); } })()";
    }

    private static int responseStatus(JsonObject response) {
        JsonElement status = response.get("status");
        if (status == null || status.isJsonNull()) {
            return -1;
        }
        try {
            return status.getAsInt();
        } catch (RuntimeException malformed) {
            return -1;
        }
    }

    private static long retryDelayMillis(JsonObject response, int retry) {
        String retryAfter = stringField(response, "retryAfter");
        if (retryAfter != null) {
            try {
                double seconds = Double.parseDouble(retryAfter.strip());
                long delay = (long) Math.ceil(seconds * 1000);
                if (delay >= 0 && delay <= MAX_RETRY_DELAY_MILLIS) {
                    return delay;
                }
            } catch (NumberFormatException notDeltaSeconds) {
                try {
                    long delay = java.time.ZonedDateTime.parse(
                            retryAfter, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli() - System.currentTimeMillis();
                    if (delay >= 0 && delay <= MAX_RETRY_DELAY_MILLIS) {
                        return delay;
                    }
                } catch (java.time.DateTimeException notHttpDate) {
                    LOG.trace("Failed to parse Retry-After as Date: {}", notHttpDate.getMessage());
                }
            }
            LOG.trace("Ignoring malformed or unreasonable Retry-After value: {}", retryAfter);
        }
        return Math.min(PAGE_DELAY_MILLIS * (1L << Math.min(retry, 3)), MAX_RETRY_DELAY_MILLIS);
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
