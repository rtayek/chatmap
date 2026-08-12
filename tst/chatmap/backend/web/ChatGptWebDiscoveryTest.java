package chatmap.backend.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ChatGptWebDiscoveryTest {

    @Test
    void multipleActivePagesUseOffsetsAndThenEnumerateArchived() {
        List<String> calls = new ArrayList<>();
        WebDiscoveryResult result = discover((offset, archived) -> {
            calls.add(archived + ":" + offset);
            if (archived) {
                return page();
            }
            return offset == 0 ? fullPage("active") : page(chat("active-final"));
        });

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of("false:0", "false:28", "true:0"), calls);
        assertEquals(29, result.discoveredCount());
    }

    @Test
    void activeAndArchivedListsDeduplicateInStableFirstSeenOrder() {
        WebDiscoveryResult result = discover((offset, archived) -> archived
                ? page(chat("a"), chat("c"))
                : page(chat("a"), chat("b"), chat("a")));

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of("a", "b", "c"), ids(result));
    }

    @Test
    void fullFinalPageIsFollowedByAnEmptyTerminalPage() {
        List<Integer> activeOffsets = new ArrayList<>();
        WebDiscoveryResult result = discover((offset, archived) -> {
            if (archived) {
                return page();
            }
            activeOffsets.add(offset);
            return offset < 56 ? fullPage("page-" + offset) : page();
        });

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of(0, 28, 56), activeOffsets);
        assertEquals(56, result.discoveredCount());
    }

    @Test
    void missingAccessTokenIsNotRetried() {
        List<String> calls = new ArrayList<>();
        WebDiscoveryResult result = discover((offset, archived) -> {
            calls.add(archived + ":" + offset);
            return "{\"error\":\"no-access-token\"}";
        });

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertEquals(List.of("false:0"), calls);
        assertTrue(result.reason().contains("no-access-token"), result.reason());
    }

    @Test
    void malformedResponseIsIncomplete() {
        WebDiscoveryResult result = discover((offset, archived) -> "not-json");

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertTrue(result.reason().contains("offset 0"), result.reason());
    }

    @Test
    void nonRetryableHttpFailureIsNotRetried() {
        List<Integer> offsets = new ArrayList<>();
        WebDiscoveryResult result = discover((offset, archived) -> {
            offsets.add(offset);
            return "{\"error\":\"HTTP 503\",\"status\":503}";
        });

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertEquals(List.of(0), offsets);
    }

    @Test
    void rateLimitHonorsRetryAfterAndRetriesTheSameOffset() {
        List<Integer> activeOffsets = new ArrayList<>();
        List<Long> sleeps = new ArrayList<>();
        WebDiscoveryResult result = ChatGptWebAdapter.enumerate((offset, archived) -> {
            if (archived) {
                return page();
            }
            activeOffsets.add(offset);
            return activeOffsets.size() == 1
                    ? "{\"error\":\"HTTP 429\",\"status\":429,\"retryAfter\":\"2\"}"
                    : page(chat("a"));
        }, sleeps::add, 10, 3);

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of(0, 0), activeOffsets);
        assertEquals(2_000L, sleeps.get(0));
        assertTrue(result.reason().contains("Recovered from 1 HTTP 429"), result.reason());
    }

    @Test
    void repeatedRateLimitsExhaustBoundedRetriesAtTheFailingOffset() {
        List<Integer> offsets = new ArrayList<>();
        WebDiscoveryResult result = ChatGptWebAdapter.enumerate((offset, archived) -> {
            offsets.add(offset);
            return "{\"error\":\"HTTP 429\",\"status\":429}";
        }, ignored -> { }, 10, 2);

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertEquals(List.of(0, 0, 0), offsets);
        assertTrue(result.reason().contains("offset 0"), result.reason());
    }

    @Test
    void retryAfterAFullPageKeepsTheCurrentOffset() {
        List<Integer> activeOffsets = new ArrayList<>();
        WebDiscoveryResult result = ChatGptWebAdapter.enumerate((offset, archived) -> {
            if (archived) {
                return page();
            }
            activeOffsets.add(offset);
            if (offset == 0) {
                return fullPage("first");
            }
            return activeOffsets.size() == 2
                    ? "{\"error\":\"HTTP 429\",\"status\":429}"
                    : page(chat("last"));
        }, ignored -> { }, 10, 3);

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of(0, 28, 28), activeOffsets);
        assertEquals(29, result.discoveredCount());
    }

    private static WebDiscoveryResult discover(ChatGptWebAdapter.ConversationPageFetcher fetcher) {
        return ChatGptWebAdapter.enumerate(fetcher, ignored -> { }, 10, 3);
    }

    private static String fullPage(String prefix) {
        String[] chats = new String[28];
        for (int i = 0; i < chats.length; i++) {
            chats[i] = chat(prefix + "-" + i);
        }
        return page(chats);
    }

    private static String chat(String id) {
        return "{\"id\":\"" + id + "\",\"title\":\"Chat " + id + "\"}";
    }

    private static String page(String... chats) {
        return "{\"items\":[" + String.join(",", chats) + "]}";
    }

    private static List<String> ids(WebDiscoveryResult result) {
        return result.conversations().stream()
                .map(summary -> summary.url().substring(summary.url().lastIndexOf('/') + 1))
                .toList();
    }
}
