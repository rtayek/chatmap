package chatmap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.SearchOptions;
import chatmap.domain.SearchResult;
import chatmap.domain.Source;
import chatmap.domain.Tag;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.SearchRepository;
import chatmap.storage.TagRepository;

class SearchServiceTest {

    private Connection conn;
    private ChatRepository chats;
    private MessageRepository messages;
    private ProjectRepository projects;
    private TagRepository tags;
    private SearchService searchService;
    private ImportService importService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        messages = new MessageRepository(conn);
        projects = new ProjectRepository(conn);
        tags = new TagRepository(conn);
        searchService = new SearchService(new SearchRepository(conn));
        importService = new ImportService(chats, messages);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void searchesMessageTextAndReturnsMatchingChats() throws Exception {
        Chat match = insertChat("Match", "2026-07-06T00:00:00Z");
        Chat miss = insertChat("Miss", "2026-07-06T00:01:00Z");
        messages.insert(new Message(0, match.id(), "user", "ChatMap search target", 0, null, null));
        messages.insert(new Message(0, miss.id(), "user", "unrelated content", 0, null, null));

        assertEquals(List.of(match), searchService.searchChats("target"));
    }

    @Test
    void simpleSearchMatchesPartialTokens() throws Exception {
        Chat match = insertChat("Match", "2026-07-06T00:00:00Z");
        messages.insert(new Message(0, match.id(), "user", "ChatMap search target", 0, null, null));

        assertEquals(List.of(match), searchService.searchChats("chat"));
    }

    @Test
    void multiWordServiceSearchMatchesAnyTokenPrefix() throws Exception {
        Chat alpha = insertChat("Alpha", "2026-07-06T00:00:00Z");
        Chat beta = insertChat("Beta", "2026-07-06T00:01:00Z");
        messages.insert(new Message(0, alpha.id(), "user", "alpha content", 0, null, null));
        messages.insert(new Message(0, beta.id(), "user", "beta content", 0, null, null));

        assertEquals(List.of(alpha, beta), searchService.searchChats("alp bet"));
    }

    @Test
    void emptySearchReturnsAllChats() throws Exception {
        Chat first = insertChat("First", "2026-07-06T00:00:00Z");
        Chat second = insertChat("Second", "2026-07-06T00:01:00Z");

        assertEquals(List.of(first, second), searchService.searchChats("   "));
    }

    @Test
    void searchAfterImportingPlainTextFindsImportedChat() throws Exception {
        Chat imported = importService.importFile(Path.of("samples", "plainTextSample.txt"));

        assertEquals(List.of(imported), searchService.searchChats("organize"));
    }

    @Test
    void clearingSearchWithEmptyQueryRestoresFullChatList() throws Exception {
        Chat first = insertChat("First", "2026-07-06T00:00:00Z");
        Chat second = insertChat("Second", "2026-07-06T00:01:00Z");
        messages.insert(new Message(0, first.id(), "user", "target", 0, null, null));

        assertEquals(List.of(first), searchService.searchChats("target"));
        assertEquals(List.of(first, second), searchService.searchChats(""));
    }

    @Test
    void searchResultsAreReturnedInDeterministicOrder() throws Exception {
        Chat second = insertChat("Second", "2026-07-06T00:01:00Z");
        Chat first = insertChat("First", "2026-07-06T00:00:00Z");
        messages.insert(new Message(0, second.id(), "user", "target", 0, null, null));
        messages.insert(new Message(0, first.id(), "user", "target", 0, null, null));

        assertEquals(List.of(first, second), searchService.searchChats("target"));
    }

    @Test
    void emptySearchResultsRestoreAllChats() throws Exception {
        Chat first = insertChat("First", "2026-07-06T00:00:00Z");
        Chat second = insertChat("Second", "2026-07-06T00:01:00Z");

        assertEquals(List.of(first.id(), second.id()), searchService.searchResults("   ").stream()
                .map(SearchResult::chatId)
                .toList());
    }

    @Test
    void emptySearchAppliesProjectAndTagFilters() throws Exception {
        Project project = projects.insert(new Project(
                0, "Filtered", null, "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));
        Tag tag = tags.insert(new Tag(0, "MVP"));
        Chat match = insertChat("Match", "2026-07-06T00:00:00Z");
        Chat projectOnly = insertChat("Project only", "2026-07-06T00:01:00Z");
        chats.assignProject(match.id(), project.id());
        chats.assignProject(projectOnly.id(), project.id());
        tags.assignToChat(match.id(), tag.id());

        List<SearchResult> results = searchService.searchResults(
                "", new SearchOptions(project.id(), tag.id(), null));

        assertEquals(List.of(match.id()), results.stream().map(SearchResult::chatId).toList());
    }

    @Test
    void oneResultIsSelectableAndLoadsNormalDetailData() throws Exception {
        Chat chat = insertChat("Selectable", "2026-07-06T00:00:00Z");
        Message message = messages.insert(new Message(0, chat.id(), "assistant", "selectable target", 0, null, null));

        SearchResult result = searchService.searchResults("target").getFirst();

        assertEquals(chat.id(), result.chatId());
        assertEquals(List.of(message), messages.findByChat(result.chatId()));
    }

    @Test
    void multipleResultsAreSelectableByChatId() throws Exception {
        Chat first = insertChat("First", "2026-07-06T00:00:00Z");
        Chat second = insertChat("Second", "2026-07-06T00:01:00Z");
        messages.insert(new Message(0, first.id(), "user", "target one", 0, null, null));
        messages.insert(new Message(0, second.id(), "user", "target two", 0, null, null));

        List<SearchResult> results = searchService.searchResults("target");

        assertEquals(List.of(first.id(), second.id()), results.stream().map(SearchResult::chatId).toList());
        assertEquals(first, results.get(0).chat());
        assertEquals(second, results.get(1).chat());
    }

    @Test
    void resultExposesChatIdProjectTagsAndSnippet() throws Exception {
        Project project = projects.insert(new Project(
                0, "Search Project", null, "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));
        Tag tag = tags.insert(new Tag(0, "MVP"));
        Chat chat = chats.insert(new Chat(
                0, project.id(), Source.plainText, "Metadata", null, null, "2026-07-06T00:00:00Z", false));
        tags.assignToChat(chat.id(), tag.id());
        messages.insert(new Message(0, chat.id(), "user", "ChatMap metadata target", 0, null, null));

        SearchResult result = searchService.searchResults("metadata").getFirst();

        assertEquals(chat.id(), result.chatId());
        assertEquals("Search Project", result.projectName());
        assertEquals(List.of(tag), result.tags());
        assertTrue(result.snippet().toLowerCase().contains("metadata"));
    }

    private Chat insertChat(String title, String importedAt) throws Exception {
        return chats.insert(new Chat(0, null, Source.plainText, title, null, null, importedAt, false));
    }
}
