package chatmap.infrastructure.provider.web;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class GeminiWebDiscoveryTest {

    @Test
    void correctContainerMustRemainAtVerifiedBottom() {
        AtomicInteger probes = new AtomicInteger();
        WebDiscoveryResult result = discover(List.of(
                state(List.of(chat("a")), true, false, true, true, false),
                state(List.of(chat("a")), true, false, true, true, false),
                state(List.of(chat("a")), true, false, true, true, false),
                state(List.of(chat("a")), true, false, true, true, false)), probes);

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(4, probes.get());
        assertTrue(result.reason().contains("nearest scrollable ancestor"), result.reason());
    }

    @Test
    void virtualizedEntriesAccumulateInFirstSeenOrder() {
        WebDiscoveryResult result = discover(List.of(
                state(List.of(chat("a"), chat("b")), true, false, true, false, true),
                state(List.of(chat("b"), chat("c")), true, false, true, false, true),
                state(List.of(chat("c")), true, false, true, true, false),
                state(List.of(chat("c")), true, false, true, true, false),
                state(List.of(chat("c")), true, false, true, true, false)), new AtomicInteger());

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(List.of("a", "b", "c"), ids(result));
    }

    @Test
    void containerNotFoundIsIncomplete() {
        WebDiscoveryResult result = discover(List.of(
                state(List.of(chat("a")), true, false, false, false, false)), new AtomicInteger());

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertTrue(result.reason().contains("could not be identified"), result.reason());
    }

    @Test
    void explicitAuthenticatedEmptyStateIsComplete() {
        WebDiscoveryResult result = discover(List.of(
                state(List.of(), true, true, false, false, false)), new AtomicInteger());

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(0, result.discoveredCount());
        assertTrue(result.reason().contains("explicit empty state"), result.reason());
    }

    @Test
    void emptyMissingListWithoutProofIsIncomplete() {
        WebDiscoveryResult result = discover(List.of(
                state(List.of(), false, false, false, false, false)), new AtomicInteger());

        assertEquals(DiscoveryStatus.incomplete, result.status());
        assertTrue(result.reason().contains("could not be verified"), result.reason());
    }

    @Test
    void slowHydrationDoesNotCausePrematureCompletion() {
        AtomicInteger probes = new AtomicInteger();
        WebDiscoveryResult result = discover(List.of(
                state(List.of(), false, false, false, false, false),
                state(List.of(), false, false, false, false, false),
                state(List.of(chat("a")), true, false, true, false, true),
                state(List.of(chat("a")), true, false, true, true, false),
                state(List.of(chat("a")), true, false, true, true, false),
                state(List.of(chat("a")), true, false, true, true, false)), probes);

        assertEquals(DiscoveryStatus.complete, result.status());
        assertEquals(6, probes.get());
        assertEquals(List.of("a"), ids(result));
    }

    private static WebDiscoveryResult discover(
            List<GeminiWebAdapter.ConversationListState> states, AtomicInteger probes) {
        return GeminiWebAdapter.discoverConversationList(() -> {
            int index = probes.getAndIncrement();
            return states.get(Math.min(index, states.size() - 1));
        }, ignored -> { }, states.size());
    }

    private static GeminiWebAdapter.ConversationListState state(
            List<CdpTranscriptAdapter.ChatWebSummary> chats,
            boolean authenticated,
            boolean empty,
            boolean container,
            boolean terminal,
            boolean moved) {
        return new GeminiWebAdapter.ConversationListState(
                chats, authenticated, empty, false, container, terminal, moved);
    }

    private static CdpTranscriptAdapter.ChatWebSummary chat(String id) {
        return new CdpTranscriptAdapter.ChatWebSummary(
                "Chat " + id, "https://gemini.google.com/app/" + id);
    }

    private static List<String> ids(WebDiscoveryResult result) {
        return result.conversations().stream()
                .map(summary -> summary.url().substring(summary.url().lastIndexOf('/') + 1))
                .toList();
    }
}
