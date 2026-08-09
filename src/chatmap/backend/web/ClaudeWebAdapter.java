package chatmap.backend.web;

import chatmap.backend.providers.ClaudeTurn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
            // Sidebar may be slow, or absent when logged out.
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
            // No messages found in time (e.g. not logged in) -> empty transcript.
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

    private static String inferRole(CdpPage.CdpLocator turn) {
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
}
