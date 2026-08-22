package chatmap.presentation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Source;
import chatmap.application.model.ChatExportModel;

final class ChatDetailRendererTest {

    @Test
    void rendersChatMetadataSummaryAndMessages() {
        Chat chat = Chat.builder()
                            .id(7)
                            .projectId(null)
                            .source(Source.chatGptWeb)
                            .title("Planning")
                            .createdAt(null)
                            .updatedAt(null)
                            .importedAt("2026-08-08T00:00:00Z")
                            .archived(false)
                            .build();
        ChatExportModel model = new ChatExportModel(chat, List.of(
                new Message(1, chat.id(), MessageRole.user, "Question", 0, null, null),
                new Message(2, chat.id(), MessageRole.assistant, "Answer", 1, null, null)));
        ChatSummary summary = new ChatSummary(3, chat.id(), "Short summary",
                "claude", "2026-08-08T00:01:00Z", "hash");

        Project relatedProject = new Project(1, "Related", null,
                "2026-08-08T00:00:00Z", "2026-08-08T00:00:00Z");

        String rendered = ChatDetailRenderer.render(model, summary, List.of(relatedProject));

        assertEquals("""
                Planning
                Source: ChatGPT web
                Imported: 2026-08-08T00:00:00Z

                Related Projects: Related

                LLM Summary (claude): Short summary

                [User]
                Question

                [Assistant]
                Answer

                """, rendered);
    }

    @Test
    void resultRowsUseTheSourceDisplayName() {
        Chat chat = Chat.builder()
                            .id(7)
                            .projectId(null)
                            .source(Source.chatGptWeb)
                            .title("Planning")
                            .createdAt(null)
                            .updatedAt(null)
                            .importedAt("2026-08-08T00:00:00Z")
                            .archived(false)
                            .build();

        String rendered = ChatMapViewBuilder.formatResultRow(
                new SearchResult(chat, null, List.of(), null));

        assertEquals("Planning\nSource: ChatGPT web", rendered);
    }

    @Test
    void resumeChatDisplayOmitsProviderSessionId() {
        Chat chat = Chat.builder()
                            .id(11)
                            .projectId(null)
                            .source(Source.claudeCliPrompt)
                            .title("Resume Target")
                            .createdAt(null)
                            .updatedAt(null)
                            .importedAt("2026-08-08T00:00:00Z")
                            .archived(false)
                            .providerSessionId("long-provider-session-id")
                            .build();

        assertEquals("Resume Target [11]", ChatMapViewBuilder.namedChatConverter().toString(chat));
    }

    @Test
    void formatsActiveChatIndicatorAndPromptTitle() {
        assertEquals("Active chat: New conversation", ChatMapApp.newConversationText());
        assertEquals("Active chat: Short task [12]", ChatMapApp.activeChatText("Short task", 12));
        assertEquals("1234567890123456789012345678901234567890...",
                ChatMapApp.promptTitle("12345678901234567890123456789012345678901"));
    }

    @Test
    void promptHistoryRendersMessagesInStoredOrder() {
        Chat chat = Chat.builder()
                            .id(9)
                            .projectId(null)
                            .source(Source.claudeCliPrompt)
                            .title("Resume Target")
                            .createdAt(null)
                            .updatedAt(null)
                            .importedAt("2026-08-08T00:00:00Z")
                            .archived(false)
                            .providerSessionId("session-abc")
                            .build();
        ChatExportModel model = new ChatExportModel(chat, List.of(
                new Message(1, chat.id(), MessageRole.user, "First", 0, null, null),
                new Message(2, chat.id(), MessageRole.assistant, "Second", 1, null, null),
                new Message(3, chat.id(), MessageRole.user, "Third", 2, null, null)));

        assertEquals("""
                Resume Target
                Source: Claude CLI prompt
                Session: session-abc

                [User]
                First

                [Assistant]
                Second

                [User]
                Third

                """, PromptResultDisplay.historyText(model));
    }
}
