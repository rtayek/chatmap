package chatmap.backend.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class PlaywrightWebAdapterTest {

    @Test
    void defaultCdpUrlIsLocalhost9222() {
        try (PlaywrightWebAdapter adapter = new PlaywrightWebAdapter()) {
            assertEquals("http://127.0.0.1:9222", adapter.cdpUrl());
            assertFalse(adapter.isConnected());
            assertFalse(adapter.isConnectedViaCdp());
        }
    }

    @Test
    void acceptsCustomCdpUrl() {
        try (PlaywrightWebAdapter adapter = new PlaywrightWebAdapter("http://localhost:9333")) {
            assertEquals("http://localhost:9333", adapter.cdpUrl());
            assertFalse(adapter.isConnected());
            assertFalse(adapter.isConnectedViaCdp());
        }
    }

    @Test
    void closeIdempotentWhenNotConnected() {
        PlaywrightWebAdapter adapter = new PlaywrightWebAdapter();
        adapter.close();
        adapter.close();
        assertFalse(adapter.isConnected());
        assertFalse(adapter.isConnectedViaCdp());
    }

    @Test
    void chatWebSummaryStoresTitleAndUrl() {
        PlaywrightWebAdapter.ChatWebSummary summary = new PlaywrightWebAdapter.ChatWebSummary(
                "MyClaw Architecture Chat", "https://chatgpt.com/c/123-abc"
        );
        assertEquals("MyClaw Architecture Chat", summary.title());
        assertEquals("https://chatgpt.com/c/123-abc", summary.url());
    }

    @Test
    void submitToTitleThrowsWhenTitleNotFound() {
        try (PlaywrightWebAdapter adapter = new PlaywrightWebAdapter()) {
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.submitToTitle("NonExistentTitle", "prompt")
            );
        }
    }
}
