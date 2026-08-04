package chatmap.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;

class MarkdownImporterTest {

    private static final String importedAt = "2026-07-06T00:00:00Z";

    private Connection conn;

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void importsMarkdownWithLevelOneHeadingAsTitle() {
        String markdown = "# Architecture Notes\n\nMarkdown body with storage details.";

        ImportedChat imported = new MarkdownImporter().importMarkdown(markdown, "fallback.md", importedAt);

        assertEquals("Architecture Notes", imported.chat().title());
        assertEquals(chatmap.domain.Source.markdown, imported.chat().source());
        assertEquals(importedAt, imported.chat().importedAt());
        assertEquals(false, imported.chat().archived());
        assertEquals(null, imported.chat().projectId());

        assertEquals(1, imported.messages().size());
        Message message = imported.messages().getFirst();
        assertEquals(MarkdownImporter.unknownRole, message.role());
        assertEquals(markdown, message.text());
        assertEquals(0, message.sequence());
        assertEquals(null, message.timestamp());
        assertEquals(null, message.rawJson());
    }

    @Test
    void importsMarkdownWithoutLevelOneHeadingUsingFallbackTitle() {
        String markdown = "## Section\n\nBody without a top heading.";

        ImportedChat imported = new MarkdownImporter().importMarkdown(markdown, "notes.md", importedAt);

        assertEquals("notes.md", imported.chat().title());
        assertEquals(chatmap.domain.Source.markdown, imported.chat().source());
        assertEquals(markdown, imported.messages().getFirst().text());
    }

    @Test
    void importsRolePrefixedMarkdownWithUserAndAssistantMessages() {
        String markdown = "# Transcript\n\n User: \n\nQuestion line\n\nQuestion detail.\n\n"
                + "ASSISTANT: Answer line\n\nAnswer detail.\n\n";

        ImportedChat imported = new MarkdownImporter().importMarkdown(markdown, "fallback.md", importedAt);

        assertEquals("Transcript", imported.chat().title());
        assertEquals(List.of("user", "assistant"),
                imported.messages().stream().map(Message::role).toList());
        assertEquals("Question line\n\nQuestion detail.",
                imported.messages().get(0).text());
        assertEquals("Answer line\n\nAnswer detail.", imported.messages().get(1).text());
    }

    @Test
    void importsRolePrefixedMarkdownWithSystemUserAndAssistantMessages() {
        String markdown = "# Transcript\n\nSystem: System note.\n"
                + "User: Question line\n"
                + "Assistant: Answer line";

        ImportedChat imported = new MarkdownImporter().importMarkdown(markdown, "fallback.md", importedAt);

        assertEquals("Transcript", imported.chat().title());
        assertEquals(List.of("system", "user", "assistant"),
                imported.messages().stream().map(Message::role).toList());
        assertEquals("System note.", imported.messages().get(0).text());
        assertEquals("Question line", imported.messages().get(1).text());
        assertEquals("Answer line", imported.messages().get(2).text());
    }

    @Test
    void rolePrefixedMarkdownDoesNotIncludeTitleHeadingInMessageText() {
        String markdown = "# Transcript\n\nUser: Question line";

        ImportedChat imported = new MarkdownImporter().importMarkdown(markdown, "fallback.md", importedAt);

        assertEquals("Transcript", imported.chat().title());
        assertEquals("Question line", imported.messages().getFirst().text());
    }

    @Test
    void rolePrefixedMarkdownIgnoresPreambleBeforeFirstRole() {
        String markdown = "Preamble note\n\nUser: Question line";

        ImportedChat imported = new MarkdownImporter().importMarkdown(markdown, "fallback.md", importedAt);

        assertEquals("fallback.md", imported.chat().title());
        assertEquals("Question line", imported.messages().getFirst().text());
    }

    @Test
    void importedMarkdownPersistsAndCanBeSearchedWithFts() throws Exception {
        String markdown = "# Searchable Markdown\n\nThis note mentions deterministic indexing.";
        ImportedChat imported = new MarkdownImporter().importMarkdown(markdown, "fallback.md", importedAt);
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        ChatRepository chats = new ChatRepository(conn);
        MessageRepository messages = new MessageRepository(conn);

        Chat storedChat = chats.insert(imported.chat());
        Message importedMessage = imported.messages().getFirst();
        Message storedMessage = messages.insert(new Message(0, storedChat.id(), importedMessage.role(),
                importedMessage.text(), importedMessage.sequence(), importedMessage.timestamp(),
                importedMessage.rawJson()));

        assertEquals(storedChat, chats.findById(storedChat.id()).orElseThrow());
        assertEquals(List.of(storedMessage), messages.findByChat(storedChat.id()));
        assertEquals(List.of(storedMessage.id()), messages.searchText("deterministic"));
    }
}
