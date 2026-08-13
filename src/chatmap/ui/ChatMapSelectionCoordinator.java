package chatmap.ui;

import chatmap.domain.ChatSummary;
import chatmap.domain.SearchResult;
import chatmap.application.model.ChatExportModel;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

/**
 * Keeps the chat list selection, the detail pane, and the export/summarize
 * button enabled-states in sync with each other and with the controller.
 *
 * {@code applyingListState} guards against reentrancy: {@link #applyListState}
 * programmatically changes the list's selection, which fires the same
 * selection listener a user click would ({@link #handleSelectedResult}); the
 * flag suppresses that reentrant call while a snapshot is being applied.
 */
final class ChatMapSelectionCoordinator {

    private final ChatMapController controller;
    private final BackgroundActionRunner backgroundActions;
    private final ListView<SearchResult> chatList;
    private final TextArea detail;
    private final Label status;
    private final Button exportChatButton;
    private final Button summarizeButton;
    private boolean applyingListState;

    ChatMapSelectionCoordinator(ChatMapController controller, BackgroundActionRunner backgroundActions,
            ListView<SearchResult> chatList, TextArea detail, Label status,
            Button exportChatButton, Button summarizeButton) {
        this.controller = controller;
        this.backgroundActions = backgroundActions;
        this.chatList = chatList;
        this.detail = detail;
        this.status = status;
        this.exportChatButton = exportChatButton;
        this.summarizeButton = summarizeButton;
    }

    void handleSelectedResult(SearchResult selectedResult) {
        if (applyingListState) {
            return;
        }
        updateSelectionActionStates();
        if (selectedResult == null) {
            detail.clear();
            return;
        }
        showChatDetails(selectedResult.chatId());
    }

    private void showChatDetails(long chatId) {
        backgroundActions.runValue(null, null,
                () -> {
                    // Record the selection and load details on the DB lane, never on the FX
                    // thread. summarize (claude CLI) and live fetch run on the separate
                    // backend lane, so this queues only behind other DB-only work, not them.
                    controller.selectChat(chatId);
                    ChatExportModel model = controller.loadChatDetails(chatId).orElse(null);
                    ChatSummary summary = model == null ? null : controller.latestSummary(chatId).orElse(null);
                    return new ChatDetail(model, summary);
                },
                loaded -> renderChatDetail(loaded.model(), loaded.summary()));
    }

    private void renderChatDetail(ChatExportModel model, ChatSummary summary) {
        if (model == null) {
            detail.clear();
            status.setText("Selected chat no longer exists.");
            return;
        }
        detail.setText(ChatDetailRenderer.render(model, summary));
    }

    void applyListState(ChatListState.Snapshot snapshot) {
        applyingListState = true;
        try {
            chatList.getSelectionModel().clearSelection();
            chatList.setItems(FXCollections.observableArrayList(snapshot.currentItems()));
        } finally {
            applyingListState = false;
        }
        status.setText(snapshot.statusText());
        if (snapshot.selectedChatId() == null) {
            detail.clear();
            updateSelectionActionStates();
        } else if (!selectChat(snapshot.selectedChatId())) {
            detail.clear();
            updateSelectionActionStates();
        }
    }

    private boolean selectChat(long chatId) {
        for (SearchResult result : chatList.getItems()) {
            if (result.chatId() == chatId) {
                chatList.getSelectionModel().select(result);
                return true;
            }
        }
        return false;
    }

    void updateSelectionActionStates() {
        boolean noSelection = chatList.getSelectionModel().getSelectedItem() == null;
        if (exportChatButton != null) {
            exportChatButton.setDisable(noSelection);
        }
        if (summarizeButton != null) {
            summarizeButton.setDisable(noSelection);
        }
    }

    Long selectedChatId() {
        SearchResult selected = chatList.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.chatId();
    }

    /** Carrier for a chat's export model plus its latest summary, loaded off the FX thread. */
    private record ChatDetail(ChatExportModel model, ChatSummary summary) {
    }
}
