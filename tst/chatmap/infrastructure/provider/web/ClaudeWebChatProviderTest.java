package chatmap.infrastructure.provider.web;


import chatmap.infrastructure.provider.ClaudeTurn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;
import chatmap.application.model.ImportedChat;

/**
 * Tests the browser-free part of the provider: turning conversation turns into an
 * ImportedChat directly (the simplification that replaced the Markdown round-trip).
 */
final class ClaudeWebChatProviderTest {

    @Test
    void buildsImportedChatFromTurnsPreservingRolesAndOrder() {
        ImportedChat chat = new ClaudeWebChatProvider().toImportedChat(
                "My session",
                List.of(new ClaudeTurn("user", "How do I center a div?"),
                        new ClaudeTurn("assistant", "Use flexbox.")),
                "2026-08-04T00:00:00Z");

        assertEquals("My session", chat.chat().title());
        assertEquals(Source.claudeWeb, chat.chat().source());
        assertEquals("2026-08-04T00:00:00Z", chat.chat().importedAt());

        List<Message> messages = chat.messages();
        assertEquals(2, messages.size());
        assertEquals(MessageRole.user, messages.get(0).role());
        assertEquals("How do I center a div?", messages.get(0).text());
        assertEquals(0, messages.get(0).sequence());
        assertEquals(MessageRole.assistant, messages.get(1).role());
        assertEquals("Use flexbox.", messages.get(1).text());
        assertEquals(1, messages.get(1).sequence());
    }

    @Test
    void extractsExternalIdentityFromClaudeUrl() {
        ImportedChat chat = new ClaudeWebChatProvider().toImportedChat(
                "My session",
                "https://claude.ai/chat/123e4567-e89b-12d3-a456-426614174000",
                List.of(new ClaudeTurn("user", "hi")),
                "2026-08-04T00:00:00Z");

        assertEquals("123e4567-e89b-12d3-a456-426614174000", chat.chat().externalConversationId());
        assertEquals("https://claude.ai/chat/123e4567-e89b-12d3-a456-426614174000", chat.chat().sourceUri());
    }

    @Test
    void blankTitleFallsBackToDefault() {
        ImportedChat chat = new ClaudeWebChatProvider().toImportedChat(
                "  ", List.of(new ClaudeTurn("user", "hi")), "2026-08-04T00:00:00Z");

        assertEquals("Claude (web) live chat", chat.chat().title());
    }

    @Test
    void unknownRolesArePreservedNotDropped() {
        ImportedChat chat = new ClaudeWebChatProvider().toImportedChat(
                "t", List.of(new ClaudeTurn("unknown", "tool output")), "2026-08-04T00:00:00Z");

        assertEquals(1, chat.messages().size());
        assertEquals(MessageRole.unknown, chat.messages().get(0).role());
    }

    @Test
    void emptyTurnsProduceNoMessages() {
        ImportedChat chat = new ClaudeWebChatProvider().toImportedChat("t", List.of(), "2026-08-04T00:00:00Z");
        assertTrue(chat.messages().isEmpty());
    }
}
