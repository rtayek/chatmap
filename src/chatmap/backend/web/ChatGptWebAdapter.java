package chatmap.backend.web;

import chatmap.backend.providers.ClaudeTurn;

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
            case "user" -> "user";
            case "assistant" -> "assistant";
            default -> null; // system, tool, etc. are not conversation content
        };
    }
}
