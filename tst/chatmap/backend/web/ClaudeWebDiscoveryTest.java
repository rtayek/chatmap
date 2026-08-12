package chatmap.backend.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class ClaudeWebDiscoveryTest {

    @Test
    void multiplePagesStopAtHasMoreFalseAndPreserveFirstSeenOrder() {
        List<Integer> offsets = new ArrayList<>();

        WebDiscoveryResult result = ClaudeWebAdapter.enumerate("org", offset -> {
            offsets.add(offset);
            return offset == 0
                    ? page(true, chat("a"), chat("b"), chat("a"))
                    : page(false, chat("b"), chat("c"));
        }, 5);

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of(0, 30), offsets);
        assertEquals(List.of("a", "b", "c"), ids(result));
    }

    @Test
    void missingOrganizationIdentityIsUnavailableWithoutCallingApi() {
        AtomicBoolean called = new AtomicBoolean();

        WebDiscoveryResult result = ClaudeWebAdapter.enumerate(null, offset -> {
            called.set(true);
            return page(false);
        }, 5);

        assertEquals(DiscoveryStatus.unavailable, result.status());
        assertFalse(called.get());
    }

    @Test
    void malformedResponsesAreIncomplete() {
        for (String response : List.of("not-json", "{}", "{\"data\":[],\"has_more\":\"yes\"}")) {
            WebDiscoveryResult result = ClaudeWebAdapter.enumerate("org", offset -> response, 5);
            assertEquals(DiscoveryStatus.incomplete, result.status(), response);
        }
    }

    @Test
    void httpErrorsAreIncompleteAndIncludeOffset() {
        WebDiscoveryResult result = ClaudeWebAdapter.enumerate("org",
                offset -> "{\"error\":\"HTTP 503\",\"status\":503}", 5);

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertTrue(result.reason().contains("offset 0"), result.reason());
        assertTrue(result.reason().contains("HTTP 503"), result.reason());
    }

    @Test
    void paginationSafetyLimitIsIncomplete() {
        WebDiscoveryResult result = ClaudeWebAdapter.enumerate("org",
                offset -> page(true, chat("id-" + offset)), 2);

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertEquals(2, result.discoveredCount());
        assertTrue(result.reason().contains("2 pages"), result.reason());
    }

    private static String chat(String id) {
        return "{\"uuid\":\"" + id + "\",\"name\":\"Chat " + id + "\"}";
    }

    private static String page(boolean hasMore, String... chats) {
        return "{\"data\":[" + String.join(",", chats) + "],\"has_more\":" + hasMore + "}";
    }

    private static List<String> ids(WebDiscoveryResult result) {
        return result.conversations().stream()
                .map(summary -> summary.url().substring(summary.url().lastIndexOf('/') + 1))
                .toList();
    }
}
