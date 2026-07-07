package chatmap.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.SearchResult;
import chatmap.domain.Source;

class ChatListStateTest {

    @Test
    void normalClickUpdatesSelectionWithoutChangingItemCount() {
        ChatListState state = new ChatListState();
        List<SearchResult> items = List.of(result(1, "First"), result(2, "Second"));
        state.showAll(items, "Ready");

        ChatListState.Snapshot snapshot = state.select(1);

        assertEquals(ChatListMode.allChats, snapshot.currentMode());
        assertEquals(2, snapshot.currentItems().size());
        assertEquals(1, snapshot.selectedChatId());
    }

    @Test
    void searchUpdatesList() {
        ChatListState state = new ChatListState();
        state.showAll(List.of(result(1, "First"), result(2, "Second")), "Ready");

        ChatListState.Snapshot snapshot = state.showSearchResults(List.of(result(2, "Second")), "1 match");

        assertEquals(ChatListMode.searchResults, snapshot.currentMode());
        assertEquals(List.of(2L), snapshot.currentItems().stream().map(SearchResult::chatId).toList());
        assertEquals(2, snapshot.selectedChatId());
        assertEquals("1 match", snapshot.statusText());
    }

    @Test
    void searchResultClickUpdatesSelectionWithoutChangingItemCount() {
        ChatListState state = new ChatListState();
        List<SearchResult> results = List.of(result(1, "First"), result(2, "Second"));
        state.showSearchResults(results, "2 matches");

        ChatListState.Snapshot snapshot = state.select(2);

        assertEquals(ChatListMode.searchResults, snapshot.currentMode());
        assertEquals(2, snapshot.currentItems().size());
        assertEquals(2, snapshot.selectedChatId());
    }

    @Test
    void clearSearchRestoresAllChats() {
        ChatListState state = new ChatListState();
        state.showSearchResults(List.of(result(2, "Second")), "1 match");

        ChatListState.Snapshot snapshot = state.showAll(List.of(result(1, "First"), result(2, "Second")), "Ready");

        assertEquals(ChatListMode.allChats, snapshot.currentMode());
        assertEquals(2, snapshot.currentItems().size());
        assertNull(snapshot.selectedChatId());
    }

    @Test
    void emptySearchRestoresAllChats() {
        ChatListState state = new ChatListState();

        ChatListState.Snapshot snapshot = state.showAll(List.of(result(1, "First"), result(2, "Second")), "Ready");

        assertEquals(ChatListMode.allChats, snapshot.currentMode());
        assertEquals(2, snapshot.currentItems().size());
        assertNull(snapshot.selectedChatId());
    }

    @Test
    void oneResultSearchDoesNotCreateDuplicateListEntries() {
        ChatListState state = new ChatListState();

        ChatListState.Snapshot snapshot = state.showSearchResults(List.of(result(1, "Only")), "1 match");

        assertEquals(1, snapshot.currentItems().size());
        assertEquals(List.of(1L), snapshot.currentItems().stream().map(SearchResult::chatId).toList());
        assertEquals(1, snapshot.selectedChatId());
    }

    private static SearchResult result(long id, String title) {
        return new SearchResult(
                new Chat(id, null, Source.plainText, title, null, null, "2026-07-07T00:00:00Z", false),
                null,
                List.of(),
                null);
    }
}
