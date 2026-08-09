package chatmap.backend.web;

import chatmap.backend.providers.SessionLines;

import chatmap.backend.providers.ClaudeTurn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Reads the most recent gemini.google.com conversation over CDP.
 *
 * Verified against the live logged-in page (2026-08-04). Gemini is an Angular app:
 * conversations are {@code [data-test-id="conversation"]} sidebar items that
 * navigate via the router on a real click (there is no conversation URL to open
 * directly), so {@link #openLatestConversation()} clicks the newest item rather
 * than navigating by URL. Once loaded, turns are {@code <user-query>} and
 * {@code <model-response>} custom elements; the clean text is in {@code .query-text}
 * (user) and {@code .markdown} (model) — the elements' own innerText carries "You
 * said…" / "Gemini said…" accessibility prefixes, which reading the inner content
 * elements avoids.
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

    /** Not used: Gemini navigates by click, so {@link #openLatestConversation()} is overridden. */
    @Override
    List<ChatWebSummary> listChats(Page page) {
        return List.of();
    }

    @Override
    Optional<OpenConversation> openLatestConversation() {
        Page page = openPage(siteBaseUrl());
        try {
            Locator conversations = page.locator("[data-test-id='conversation']");
            if (conversations.count() == 0) {
                // Expand the side nav so the conversation list renders.
                page.locator("chat-app-side-nav-menu-button button, button[aria-label*='menu' i]")
                        .first().click(new Locator.ClickOptions().setTimeout(4000));
                page.waitForTimeout(1500);
                conversations = page.locator("[data-test-id='conversation']");
            }
            if (conversations.count() == 0) {
                return Optional.empty();
            }
            Locator newest = conversations.first();
            String title = WebTranscripts.collapseRepeatedLines(WebTranscripts.safeInnerText(newest));
            newest.click(new Locator.ClickOptions().setTimeout(6000));
            page.waitForTimeout(3000);
            return Optional.of(new OpenConversation(
                    title == null || title.isBlank() ? "Gemini conversation" : title, page.url(), page));
        } catch (Exception unavailable) {
            return Optional.empty();
        }
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
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }
                turns.add(new ClaudeTurn(role, text.strip()));
            }
        } catch (RuntimeException ignored) {
            // return whatever parsed so far (empty on malformed input)
        }
        return turns;
    }
}
