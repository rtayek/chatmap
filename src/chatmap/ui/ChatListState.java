package chatmap.ui;

import java.util.List;

import chatmap.domain.SearchResult;

/** Small state model for the chat list; selection never changes the item list. */
public final class ChatListState {

    private ChatListMode currentMode = ChatListMode.allChats;
    private List<SearchResult> currentItems = List.of();
    private Long selectedChatId;
    private String statusText = "Ready";

    public Snapshot snapshot() {
        return new Snapshot(currentMode, currentItems, selectedChatId, statusText);
    }

    public Snapshot showAll(List<SearchResult> items, String statusText) {
        currentMode = ChatListMode.allChats;
        currentItems = copy(items);
        selectedChatId = null;
        this.statusText = statusText;
        return snapshot();
    }

    public Snapshot showSearchResults(List<SearchResult> items, String statusText) {
        currentMode = ChatListMode.searchResults;
        currentItems = copy(items);
        selectedChatId = currentItems.size() == 1 ? currentItems.getFirst().chatId() : null;
        this.statusText = statusText;
        return snapshot();
    }

    public Snapshot select(long chatId) {
        if (containsChat(chatId)) {
            selectedChatId = chatId;
        }
        return snapshot();
    }

    private boolean containsChat(long chatId) {
        return currentItems.stream().anyMatch(item -> item.chatId() == chatId);
    }

    private static List<SearchResult> copy(List<SearchResult> items) {
        return items == null ? List.of() : List.copyOf(items);
    }

    public record Snapshot(
            ChatListMode currentMode,
            List<SearchResult> currentItems,
            Long selectedChatId,
            String statusText) {

        public Snapshot {
            currentItems = currentItems == null ? List.of() : List.copyOf(currentItems);
        }
    }
}
