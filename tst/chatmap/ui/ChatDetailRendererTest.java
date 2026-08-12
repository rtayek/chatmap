package chatmap.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.domain.Message;
import chatmap.domain.MessageRole;
import chatmap.domain.Source;
import chatmap.exporter.ChatExportModel;

final class ChatDetailRendererTest {

    @Test
    void rendersChatMetadataSummaryAndMessages() {
        Chat chat = new Chat(7, null, Source.chatGptWeb, "Planning",
                null, null, "2026-08-08T00:00:00Z", false);
        ChatExportModel model = new ChatExportModel(chat, List.of(
                new Message(1, chat.id(), MessageRole.user, "Question", 0, null, null),
                new Message(2, chat.id(), MessageRole.assistant, "Answer", 1, null, null)));
        ChatSummary summary = new ChatSummary(3, chat.id(), "Short summary",
                "claude", "2026-08-08T00:01:00Z", "hash");

        String rendered = ChatDetailRenderer.render(model, summary);

        assertEquals("""
                Planning
                Source: chatGptWeb
                Imported: 2026-08-08T00:00:00Z

                AI Summary (claude): Short summary

                [user]
                Question

                [assistant]
                Answer

                """, rendered);
    }
}
