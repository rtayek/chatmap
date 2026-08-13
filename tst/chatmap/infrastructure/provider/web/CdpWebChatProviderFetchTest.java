package chatmap.infrastructure.provider.web;


import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import chatmap.application.port.provider.ChatProviderException;
import chatmap.infrastructure.provider.ClaudeTurn;
import chatmap.application.port.provider.NoImportableContentException;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.Source;

final class CdpWebChatProviderFetchTest {

    @Test
    void emptyConversationIsReportedAsNoImportableContent() {
        CdpTranscriptAdapter adapter = new EmptyAdapter();
        CdpWebChatProvider provider = provider(adapter);

        assertThrows(NoImportableContentException.class, () -> provider.fetch(candidate()));
        assertTrue(adapter.lastUnavailableReason().isEmpty());
    }

    @Test
    void adapterFailureIsReportedAsProviderFailure() {
        CdpTranscriptAdapter adapter = new FailingAdapter();
        CdpWebChatProvider provider = provider(adapter);

        ChatProviderException failure = assertThrows(
                ChatProviderException.class, () -> provider.fetch(candidate()));

        assertTrue(failure.getMessage().contains("IllegalStateException: selector failed"));
        assertTrue(adapter.lastUnavailableReason().isPresent());
    }

    private static ConversationCandidate candidate() {
        return new ConversationCandidate(
                Source.chatGptWeb, "chat-1", "Chat", "https://example.test/chat/1", null);
    }

    private static CdpWebChatProvider provider(CdpTranscriptAdapter adapter) {
        return new CdpWebChatProvider(
                "Test web",
                "Test web chat",
                Source.chatGptWeb,
                new ChromeCdpLauncher(),
                adapter,
                "http://127.0.0.1:9",
                "test provider") {
            @Override
            protected String extractIdentity(String url) {
                return url;
            }
        };
    }

    private static class EmptyAdapter extends CdpTranscriptAdapter {
        EmptyAdapter() {
            super("http://127.0.0.1:9");
        }

        @Override
        String siteBaseUrl() {
            return "https://example.test";
        }

        @Override
        Optional<OpenConversation> openConversation(ChatWebSummary summary) {
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

    private static final class FailingAdapter extends EmptyAdapter {
        @Override
        Optional<OpenConversation> openConversation(ChatWebSummary summary) {
            throw new IllegalStateException("selector failed");
        }
    }
}
