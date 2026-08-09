package chatmap.backend.web;

import chatmap.backend.providers.DefaultChatProviders;

import chatmap.backend.providers.ChatProvider;

import chatmap.backend.providers.ClaudeTurn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import chatmap.domain.Message;
import chatmap.domain.Source;
import chatmap.importer.ImportedChat;

/**
 * Browser-free tests for the web providers: the shared transcript helpers, the
 * ChatGPT author-role mapping, the Gemini turn-JSON parsing, and provider build.
 * The live DOM reading itself needs a logged-in browser and is verified manually.
 */
class WebProvidersTest {

    // --- WebTranscripts (shared) ---

    @Test
    void mergesConsecutiveSameRoleTurns() {
        List<ClaudeTurn> merged = WebTranscripts.mergeConsecutiveSameRole(List.of(
                new ClaudeTurn("user", "hi"),
                new ClaudeTurn("assistant", "para one"),
                new ClaudeTurn("assistant", "para two"),
                new ClaudeTurn("user", "thanks")));

        assertEquals(List.of("user", "assistant", "user"),
                merged.stream().map(ClaudeTurn::role).toList());
        assertEquals("para one\n\npara two", merged.get(1).text());
    }

    @Test
    void collapseRepeatedLinesDedupesSplitTitle() {
        assertEquals("My Chat", WebTranscripts.collapseRepeatedLines("My Chat\nMy Chat"));
        assertEquals("My Chat", WebTranscripts.collapseRepeatedLines("My Chat"));
        assertNull(WebTranscripts.collapseRepeatedLines(null));
    }

    @Test
    void toImportedChatUsesFallbackTitleWhenBlank() {
        ImportedChat chat = WebTranscripts.toImportedChat(
                "  ", List.of(new ClaudeTurn("user", "hi")), "2026-08-04T00:00:00Z", "Fallback");
        assertEquals("Fallback", chat.chat().title());
        assertEquals(Source.markdown, chat.chat().source());
    }

    // --- ChatGPT role mapping ---

    @Test
    void chatGptMapsUserAndAssistantAndSkipsRest() {
        assertEquals("user", ChatGptWebAdapter.mapRole("user"));
        assertEquals("assistant", ChatGptWebAdapter.mapRole("assistant"));
        assertEquals("assistant", ChatGptWebAdapter.mapRole("ASSISTANT"));
        assertNull(ChatGptWebAdapter.mapRole("system"));
        assertNull(ChatGptWebAdapter.mapRole("tool"));
        assertNull(ChatGptWebAdapter.mapRole(null));
    }

    @Test
    void chatGptProviderBuildsChatWithSequentialMessages() {
        ImportedChat chat = ChatGptWebChatProvider.toImportedChat(
                "GPT session",
                "https://chatgpt.com/c/chat-123",
                List.of(new ClaudeTurn("user", "q"), new ClaudeTurn("assistant", "a")),
                "2026-08-04T00:00:00Z");
        assertEquals("GPT session", chat.chat().title());
        assertEquals(Source.chatGptWeb, chat.chat().source());
        assertEquals("chat-123", chat.chat().externalConversationId());
        List<Message> m = chat.messages();
        assertEquals(0, m.get(0).sequence());
        assertEquals(1, m.get(1).sequence());
    }

    @Test
    void chatGptCandidateMappingDeduplicatesByExternalIdentity() {
        List<CdpTranscriptAdapter.ChatWebSummary> summaries = List.of(
                new CdpTranscriptAdapter.ChatWebSummary("First", "https://chatgpt.com/c/abc"),
                new CdpTranscriptAdapter.ChatWebSummary("Duplicate", "https://chatgpt.com/c/abc?model=x"),
                new CdpTranscriptAdapter.ChatWebSummary("Second", "https://chatgpt.com/c/def"));

        var candidates = ChatGptWebChatProvider.candidates(summaries);

        assertEquals(List.of("abc", "def"),
                candidates.stream().map(chatmap.domain.ConversationCandidate::externalConversationId).toList());
        assertEquals("First", candidates.getFirst().title());
    }

    // --- Gemini turn JSON parsing ---

    @Test
    void geminiParsesUserAndModelTurnsFromJson() {
        String json = "[{\"role\":\"user\",\"text\":\"hello\"},"
                + "{\"role\":\"assistant\",\"text\":\"hi there\"},"
                + "{\"role\":\"user\",\"text\":\"  \"}]"; // blank dropped
        List<ClaudeTurn> turns = GeminiWebAdapter.parseTurnsJson(json);
        assertEquals(2, turns.size());
        assertEquals("user", turns.get(0).role());
        assertEquals("hi there", turns.get(1).text());
    }

    @Test
    void geminiParseIsEmptyOnMalformedJson() {
        assertTrue(GeminiWebAdapter.parseTurnsJson("not json").isEmpty());
        assertTrue(GeminiWebAdapter.parseTurnsJson("{}").isEmpty());
    }

    @Test
    void geminiProviderUsesFallbackTitle() {
        ImportedChat chat = GeminiWebChatProvider.toImportedChat(
                null, List.of(new ClaudeTurn("assistant", "x")), "2026-08-04T00:00:00Z");
        assertEquals("Gemini (web) live chat", chat.chat().title());
        assertEquals(Source.geminiWeb, chat.chat().source());
        assertEquals(null, chat.chat().externalConversationId());
    }

    @Test
    void geminiCandidateMappingAllowsMissingDurableIdentity() {
        List<CdpTranscriptAdapter.ChatWebSummary> summaries = List.of(
                new CdpTranscriptAdapter.ChatWebSummary("Temporary", "gemini-sidebar-index:0"),
                new CdpTranscriptAdapter.ChatWebSummary("Durable", "https://gemini.google.com/app/abc"));

        var candidates = GeminiWebChatProvider.candidates(summaries);

        assertEquals(null, candidates.get(0).externalConversationId());
        assertEquals("abc", candidates.get(1).externalConversationId());
    }

    // --- ordering: all six providers, web first ---

    @Test
    void defaultOrderListsAllSixWebFirst() {
        List<String> names = DefaultChatProviders.ordered().stream().map(ChatProvider::name).toList();
        assertEquals(List.of(
                "Claude (web)", "ChatGPT (web)", "Gemini (web)",
                "Claude Code (CLI)", "Codex (CLI)", "Gemini (CLI)"), names);
    }
}
