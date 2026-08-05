package chatmap.backend;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Reads the most recent gemini.google.com conversation over CDP.
 *
 * UNVERIFIED: unlike the Claude and ChatGPT web paths, gemini.google.com's DOM
 * could not be checked against a live logged-in page when this was written, and
 * there was no existing code to port. The selectors below are a best-effort guess
 * at Gemini's Angular structure (user turns in {@code <user-query>}, model turns
 * in {@code <model-response>} custom elements). If they do not match, the adapter
 * returns empty cleanly (no crash) — the provider then falls through like any other
 * unavailable source. These selectors MUST be verified/adjusted against the live
 * page before this provider can be relied on.
 */
public final class GeminiWebAdapter extends CdpTranscriptAdapter {

    static final String BASE_URL = "https://gemini.google.com/app";

    GeminiWebAdapter(String cdpUrl) {
        super(cdpUrl);
    }

    @Override
    String siteBaseUrl() {
        return BASE_URL;
    }

    @Override
    List<ChatWebSummary> listChats(Page page) {
        Objects.requireNonNull(page, "page");
        try {
            page.waitForSelector("a[href*='/app/']", new Page.WaitForSelectorOptions().setTimeout(4000));
        } catch (Exception ignored) {
            // Recent-conversation links may be absent; fall back to the app page itself below.
        }

        List<ChatWebSummary> summaries = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        Locator links = page.locator("a[href*='/app/']");
        int count = links.count();
        for (int i = 0; i < count; i++) {
            Locator link = links.nth(i);
            String href = link.getAttribute("href");
            if (href == null || href.isBlank() || href.endsWith("/app")) {
                continue;
            }
            String fullUrl = href.startsWith("/") ? "https://gemini.google.com" + href : href;
            String title = WebTranscripts.collapseRepeatedLines(WebTranscripts.firstNonBlank(
                    WebTranscripts.safeInnerText(link), link.getAttribute("aria-label")));
            if (title == null || title.isBlank()) {
                title = "Untitled Chat";
            }
            if (seenUrls.add(fullUrl)) {
                summaries.add(new ChatWebSummary(title, fullUrl));
            }
        }
        // If no named conversations were found, treat the current app page (which shows
        // the latest conversation) as the single candidate.
        if (summaries.isEmpty()) {
            summaries.add(new ChatWebSummary("Gemini conversation", page.url()));
        }
        return summaries;
    }

    @Override
    List<ClaudeTurn> readTurns(Page page) {
        Objects.requireNonNull(page, "page");
        try {
            page.waitForSelector("user-query, model-response",
                    new Page.WaitForSelectorOptions().setTimeout(5000));
        } catch (Exception ignored) {
            // No messages in time -> empty transcript.
        }

        Object raw = page.evaluate("() => {"
                + "const nodes=[...document.querySelectorAll('user-query, model-response')];"
                + "return JSON.stringify(nodes.map(n=>({"
                + "role:n.tagName.toLowerCase()==='user-query'?'user':'assistant',"
                + "text:(n.innerText||'').trim()})));}");
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
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }
                turns.add(new ClaudeTurn(role, text.strip()));
            }
        } catch (RuntimeException notJson) {
            // return whatever parsed so far (empty on malformed input)
        }
        return turns;
    }
}
