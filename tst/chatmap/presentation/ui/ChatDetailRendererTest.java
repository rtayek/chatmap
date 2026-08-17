package chatmap.presentation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
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

        String rendered = ChatDetailRenderer.render(model, summary);

        assertEquals("""
                Planning
                Source: ChatGPT web
                Imported: 2026-08-08T00:00:00Z

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
}
