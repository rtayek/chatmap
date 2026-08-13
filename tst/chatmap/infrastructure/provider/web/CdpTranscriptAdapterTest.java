package chatmap.infrastructure.provider.web;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import chatmap.infrastructure.provider.ClaudeTurn;
import org.junit.jupiter.api.Test;

class CdpTranscriptAdapterTest {

    @Test
    void recordsSafeUnavailableDiagnosticWhenAdapterFails() {
        CdpTranscriptAdapter adapter = new FailingAdapter("http://127.0.0.1:9");

        assertTrue(adapter.latestTranscript().isEmpty());

        assertEquals(Optional.of("IllegalStateException: forced failure"),
                adapter.lastUnavailableReason());
    }

    @Test
    void clearsUnavailableDiagnosticBeforeNextAttempt() {
        FlakyAdapter adapter = new FlakyAdapter("http://127.0.0.1:9");

        assertTrue(adapter.latestTranscript().isEmpty());
        assertTrue(adapter.lastUnavailableReason().isPresent());

        assertTrue(adapter.latestTranscript().isEmpty());
        assertTrue(adapter.lastUnavailableReason().isEmpty());
    }

    private static final class FailingAdapter extends CdpTranscriptAdapter {
        FailingAdapter(String cdpUrl) {
            super(cdpUrl);
        }

        @Override
        String siteBaseUrl() {
            return "https://example.test";
        }

        @Override
        Optional<OpenConversation> openLatestConversation() {
            throw new IllegalStateException("forced failure");
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

    private static final class FlakyAdapter extends CdpTranscriptAdapter {
        private int calls;

        FlakyAdapter(String cdpUrl) {
            super(cdpUrl);
        }

        @Override
        String siteBaseUrl() {
            return "https://example.test";
        }

        @Override
        Optional<OpenConversation> openLatestConversation() {
            calls++;
            if (calls == 1) {
                throw new IllegalStateException("first failure");
            }
            return Optional.empty();
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
}
