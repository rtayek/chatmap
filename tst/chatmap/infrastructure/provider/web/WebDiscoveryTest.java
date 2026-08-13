package chatmap.infrastructure.provider.web;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chatmap.infrastructure.provider.ClaudeTurn;
import chatmap.infrastructure.provider.ProviderIdentity;

import com.google.gson.JsonObject;

/**
 * Deterministic tests for the exhaustive scroll-and-accumulate discovery
 * algorithm in {@link CdpTranscriptAdapter}. Everything here runs against a
 * scripted {@link ScriptedAdapter} test double (fake batches/scroll outcomes,
 * zero real wait time) — no live provider account or browser is needed.
 */
class WebDiscoveryTest {

    /** listChats()/scrollForMore() are fully scripted in these tests; nothing here should reach a real page. */
    private static final CdpTransport NEVER_CALLED_TRANSPORT = new CdpTransport() {
        @Override
        public JsonObject send(String method, Map<String, ?> params) {
            throw new UnsupportedOperationException("unexpected CDP call: " + method);
        }

        @Override
        public void close() {
        }
    };

    private static CdpTranscriptAdapter.ChatWebSummary chat(String id) {
        return new CdpTranscriptAdapter.ChatWebSummary("Chat " + id, "https://example.test/chat/" + id);
    }

    private static CdpTranscriptAdapter.ScrollAttempt moved() {
        return new CdpTranscriptAdapter.ScrollAttempt(true, 0, 100);
    }

    private static CdpTranscriptAdapter.ScrollAttempt stable() {
        return new CdpTranscriptAdapter.ScrollAttempt(true, 100, 100);
    }

    private static CdpTranscriptAdapter.ScrollAttempt notFound() {
        return new CdpTranscriptAdapter.ScrollAttempt(false, 0, 0);
    }

    @Test
    void multipleLazyLoadedBatchesAccumulateAcrossRounds() {
        ScriptedAdapter adapter = new ScriptedAdapter(
                List.of(
                        List.of(chat("a"), chat("b"), chat("c")),
                        List.of(chat("a"), chat("b"), chat("c"), chat("d"), chat("e")),
                        List.of(chat("a"), chat("b"), chat("c"), chat("d"), chat("e"), chat("f"), chat("g"))),
                List.of(moved(), moved(), moved(), stable(), stable(), stable()),
                20);

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(7, result.discoveredCount());
        assertEquals(List.of("a", "b", "c", "d", "e", "f", "g"), ids(result));
    }

    @Test
    void virtualizedListRemovesEarlierDomEntriesButAccumulationKeepsThem() {
        // Each round's batch is a sliding window -- earlier entries vanish from
        // the "DOM" (the fake batch) exactly like a virtualized list recycling
        // rows as the user scrolls. The accumulated result must still be the
        // union of everything ever seen, not just the final window.
        ScriptedAdapter adapter = new ScriptedAdapter(
                List.of(
                        List.of(chat("a"), chat("b"), chat("c")),
                        List.of(chat("c"), chat("d"), chat("e")),
                        List.of(chat("e"), chat("f"), chat("g")),
                        List.of(chat("f"), chat("g"))),
                List.of(moved(), moved(), moved(), stable(), stable(), stable()),
                20);

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of("a", "b", "c", "d", "e", "f", "g"), ids(result));
    }

    @Test
    void duplicateIdsAcrossAndWithinBatchesDeduplicate() {
        ScriptedAdapter adapter = new ScriptedAdapter(
                List.of(
                        List.of(chat("a"), chat("b"), chat("a")),
                        List.of(chat("a"), chat("b"), chat("c")),
                        List.of(chat("a"), chat("b"), chat("c"))),
                List.of(moved(), moved(), stable(), stable(), stable()),
                20);

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(3, result.discoveredCount());
        assertEquals(List.of("a", "b", "c"), ids(result));
    }

    @Test
    void scrollStillMovingKeepsSearchingEvenWhenARoundAddsNothingNew() {
        // Round 1 (index 1) adds no new item even though the scroll position
        // genuinely advanced -- a slow lazy load, not the true end. The stable
        // counter must NOT start accumulating from that round; "b" is only
        // found once the load catches up on round 2.
        ScriptedAdapter adapter = new ScriptedAdapter(
                List.of(
                        List.of(chat("a")),
                        List.of(chat("a")),
                        List.of(chat("a"), chat("b"))),
                List.of(moved(), moved(), moved(), stable(), stable(), stable()),
                20);

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of("a", "b"), ids(result));
    }

    @Test
    void completionReasonNamesTheVerifiedTerminalCondition() {
        ScriptedAdapter adapter = new ScriptedAdapter(
                List.of(List.of(chat("a"))),
                List.of(moved(), stable(), stable(), stable()),
                20);

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.complete, result.status());
        assertTrue(result.reason().contains("Complete for the normal conversation list"));
        assertTrue(result.reason().contains("scroll position and discovered count both stayed unchanged"));
    }

    @Test
    void aFixedPointAloneDoesNotProveCompletion() {
        // The batch never grows past "a", but the scroll position keeps
        // reportedly advancing every round (e.g. a carousel/dummy scroll) --
        // "no new items" alone must never be trusted as the terminal signal.
        // With a small round cap this must exhaust as incomplete, not complete.
        ScriptedAdapter adapter = new ScriptedAdapter(
                List.of(List.of(chat("a"))),
                List.of(moved()),
                6);

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertTrue(result.reason().contains("iteration limit"), result.reason());
        assertEquals(List.of("a"), ids(result));
    }

    @Test
    void selectorFailureIsReportedAsIncompleteNotFailed() {
        ScriptedAdapter adapter = new ScriptedAdapter(List.of(chat("a")), 20) {
            @Override
            List<CdpTranscriptAdapter.ChatWebSummary> listChats(CdpPage page) {
                throw new IllegalStateException("selector not found");
            }
        };

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertTrue(result.reason().contains("Selector failure"), result.reason());
        assertTrue(result.reason().contains("selector not found"), result.reason());
        assertTrue(adapter.lastUnavailableReason().isPresent());
    }

    @Test
    void unavailableStatusFromDoDiscoverAllPassesThroughAndSetsLastUnavailableReason() {
        CdpTranscriptAdapter adapter = new BareAdapter() {
            @Override
            WebDiscoveryResult doDiscoverAll() {
                return WebDiscoveryResult.unavailable(providerLabel(), "not logged in");
            }
        };

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.unavailable, result.status());
        assertEquals("not logged in", result.reason());
        assertEquals(Boolean.TRUE, adapter.lastUnavailableReason().map(r -> r.equals("not logged in")).orElse(false));
    }

    @Test
    void unexpectedExceptionDuringDiscoveryIsReportedAsFailed() {
        CdpTranscriptAdapter adapter = new BareAdapter() {
            @Override
            WebDiscoveryResult doDiscoverAll() {
                throw new IllegalStateException("boom");
            }
        };

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.failed, result.status());
        assertTrue(result.reason().contains("IllegalStateException: boom"), result.reason());
        assertTrue(adapter.lastUnavailableReason().isPresent());
    }

    @Test
    void discoveryOrderIsFirstSeenOrderNotLatestBatchOrder() {
        ScriptedAdapter adapter = new ScriptedAdapter(
                List.of(
                        List.of(chat("a"), chat("b"), chat("c")),
                        // Same three ids, deliberately reordered, plus one new id -- the
                        // accumulated order must stay a,b,c,d (first-seen), not c,b,a,d.
                        List.of(chat("c"), chat("b"), chat("a"), chat("d"))),
                List.of(moved(), moved(), stable(), stable(), stable()),
                20);

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of("a", "b", "c", "d"), ids(result));
    }

    @Test
    void missingScrollContainerCannotProveAnEmptyDiscoveryComplete() {
        List<CdpTranscriptAdapter.ChatWebSummary> noChats = List.of();
        ScriptedAdapter adapter = new ScriptedAdapter(List.of(noChats),
                List.of(notFound(), notFound(), notFound()), 20);

        WebDiscoveryResult result = adapter.discoverAll();

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertTrue(result.conversations().isEmpty());
        assertTrue(result.reason().contains("iteration limit"), result.reason());
    }

    @Test
    void claudeIdentityExtractionMatchesChatPathSegment() {
        ClaudeWebAdapter adapter = new ClaudeWebAdapter("http://127.0.0.1:9");
        assertEquals("abc-123", adapter.identityOf(
                new CdpTranscriptAdapter.ChatWebSummary("t", "https://claude.ai/chat/abc-123")));
        assertEquals("abc-123", ProviderIdentity.claudeWebId("https://claude.ai/chat/abc-123"));
    }

    @Test
    void chatGptIdentityExtractionMatchesCPathSegment() {
        ChatGptWebAdapter adapter = new ChatGptWebAdapter("http://127.0.0.1:9");
        assertEquals("xyz-9", adapter.identityOf(
                new CdpTranscriptAdapter.ChatWebSummary("t", "https://chatgpt.com/c/xyz-9")));
        assertEquals("xyz-9", ProviderIdentity.chatGptWebId("https://chatgpt.com/c/xyz-9"));
    }

    @Test
    void geminiIdentityExtractionMatchesAppPathSegmentAndFallsBackForSentinelUrls() {
        GeminiWebAdapter adapter = new GeminiWebAdapter("http://127.0.0.1:9");
        assertEquals("deadbeef", adapter.identityOf(
                new CdpTranscriptAdapter.ChatWebSummary("t", "https://gemini.google.com/app/deadbeef")));
        // The sidebar-index sentinel used when no durable href was found carries no
        // real identity; the base algorithm falls back to the summary's URL itself.
        assertNull(adapter.identityOf(
                new CdpTranscriptAdapter.ChatWebSummary("t", GeminiWebAdapter.SIDEBAR_URI_PREFIX + "3")));
    }

    /** Common no-op base for tests that only need discoverAll()'s outer wiring. */
    private static class BareAdapter extends CdpTranscriptAdapter {
        BareAdapter() {
            super("http://127.0.0.1:9");
        }

        @Override
        String siteBaseUrl() {
            return "https://example.test";
        }

        @Override
        List<ChatWebSummary> listChats(CdpPage page) {
            return List.of();
        }

        @Override
        List<ClaudeTurn> readTurns(CdpPage page) {
            return List.of();
        }
    }

    /**
     * Test double that scripts {@link #listChats} and {@link #scrollForMore}
     * from fixed lists indexed by round (the last entry repeats once a list is
     * exhausted), never touching a real {@link CdpPage} evaluate call, and
     * with no real wait between rounds so the whole suite runs instantly.
     */
    private static class ScriptedAdapter extends CdpTranscriptAdapter {
        private final List<List<ChatWebSummary>> batches;
        private final List<ScrollAttempt> scrolls;
        private final int maxRounds;
        private int round;

        ScriptedAdapter(List<List<ChatWebSummary>> batches, List<ScrollAttempt> scrolls, int maxRounds) {
            super("http://127.0.0.1:9");
            this.batches = batches;
            this.scrolls = scrolls;
            this.maxRounds = maxRounds;
        }

        ScriptedAdapter(List<ChatWebSummary> singleBatch, int maxRounds) {
            this(List.of(singleBatch), List.of(new ScrollAttempt(true, 0, 0)), maxRounds);
        }

        @Override
        String siteBaseUrl() {
            return "https://example.test";
        }

        @Override
        List<ClaudeTurn> readTurns(CdpPage page) {
            return List.of();
        }

        /**
         * Bypasses the default's real {@code openPage(...)} CDP round trip (this
         * test double never has a live browser) and drives {@link #accumulate}
         * directly against a {@link CdpPage} whose transport must never actually
         * be invoked, since {@link #listChats} and {@link #scrollForMore} are
         * both fully scripted below.
         */
        @Override
        WebDiscoveryResult doDiscoverAll() {
            return accumulate(new CdpPage(NEVER_CALLED_TRANSPORT));
        }

        @Override
        List<ChatWebSummary> listChats(CdpPage page) {
            return batches.get(Math.min(round, batches.size() - 1));
        }

        @Override
        ScrollAttempt scrollForMore(CdpPage page) {
            ScrollAttempt attempt = scrolls.get(Math.min(round, scrolls.size() - 1));
            round++;
            return attempt;
        }

        @Override
        long scrollWaitMillis() {
            return 0;
        }

        @Override
        int maxDiscoveryRounds() {
            return maxRounds;
        }
    }

    private static List<String> ids(WebDiscoveryResult result) {
        return result.conversations().stream()
                .map(summary -> summary.url().substring(summary.url().lastIndexOf('/') + 1))
                .toList();
    }
}
