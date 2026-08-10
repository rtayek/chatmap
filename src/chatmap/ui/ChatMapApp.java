package chatmap.ui;

import java.util.List;
import java.util.Optional;

import chatmap.app.ChatMapRuntime;
import chatmap.domain.Chat;
import chatmap.domain.ConversationInventory;
import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Minimal JavaFX list/detail UI for file import and selected-chat Markdown export. */
public final class ChatMapApp extends Application {

    private ChatMapRuntime runtime;
    private ChatMapController controller;
    private ListView<SearchResult> chatList;
    private TextArea detail;
    private TextField searchField;
    private ComboBox<Project> projectChoice;
    private ComboBox<Tag> tagChoice;
    private Button getLatestChatButton;
    private Button inventoryButton;
    private Button summarizeButton;
    private ComboBox<Integer> fontSizeChoice;
    private FontSizeState fontSizeState;
    private BorderPane root;
    private Label status;
    private BackgroundActionRunner backgroundActions;
    private ChatMapSelectionCoordinator selection;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        runtime = ChatMapRuntime.open(getParameters().getRaw());
        controller = runtime.controller();

        fontSizeState = new FontSizeState();
        status = new Label("Ready");
        backgroundActions = new BackgroundActionRunner(runtime, status, this::reportError);
        chatList = ChatMapViewBuilder.createChatListView(
                (observable, previousResult, selectedResult) -> selection.handleSelectedResult(selectedResult));
        detail = ChatMapViewBuilder.createDetailTextArea();

        ChatMapViewBuilder.ToolbarWidgets toolbarWidgets = ChatMapViewBuilder.createToolbar(
                fontSizeState.current(),
                () -> importFile("Import text", "*.txt"),
                () -> importFile("Import Markdown", "*.md", "*.markdown"),
                () -> importFile("Import ChatGPT JSON", "*.json"),
                this::importChatGptArchive,
                this::exportSelectedChat,
                this::getLatestChat,
                this::showConversationInventory,
                this::summarizeSelectedChat,
                size -> applyFontSize(fontSizeState.set(size)),
                this::reportError);
        getLatestChatButton = toolbarWidgets.getLatestChatButton();
        inventoryButton = toolbarWidgets.inventoryButton();
        summarizeButton = toolbarWidgets.summarizeButton();
        fontSizeChoice = toolbarWidgets.fontSizeChoice();

        selection = new ChatMapSelectionCoordinator(controller, backgroundActions, chatList, detail, status,
                toolbarWidgets.exportChatButton(), summarizeButton);

        ChatMapViewBuilder.SearchBarWidgets searchBarWidgets = ChatMapViewBuilder.createSearchBar(
                this::searchChats, this::clearSearchAndFilters, this::reportError);
        searchField = searchBarWidgets.searchField();

        ChatMapViewBuilder.ProjectBarWidgets projectBarWidgets = ChatMapViewBuilder.createProjectBar(
                this::createProject, this::assignProject, this::clearProject, this::filterByProject,
                this::reportError);
        projectChoice = projectBarWidgets.projectChoice();

        ChatMapViewBuilder.TagBarWidgets tagBarWidgets = ChatMapViewBuilder.createTagBar(
                this::createTag, this::addTag, this::removeTag, this::filterByTag,
                this::clearSearchAndFilters, this::reportError);
        tagChoice = tagBarWidgets.tagChoice();

        SplitPane content = new SplitPane(chatList, detail);
        content.setDividerPositions(0.32);
        root = ChatMapViewBuilder.assembleRootPane(toolbarWidgets.toolBar(), searchBarWidgets.searchBar(),
                projectBarWidgets.projectBar(), tagBarWidgets.tagBar(), content, status);

        refreshOrganizationChoices();
        runInBackground("Loading chats...", null, () -> controller.loadAllChats());
        stage.setTitle("ChatMap");
        applyFontSize(fontSizeState.current());
        Scene scene = new Scene(root, 900, 600);
        registerFontShortcuts(scene);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (runtime != null) {
            runtime.close();
        }
    }

    private void importFile(String title, String... patterns) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(title, patterns));
        java.io.File file = chooser.showOpenDialog(chatList.getScene().getWindow());
        if (file == null) {
            return;
        }
        runInBackground("Importing file...", null, () -> controller.importFile(file.toPath()));
    }

    private void exportSelectedChat() {
        SearchResult selectedResult = chatList.getSelectionModel().getSelectedItem();
        Chat selected = selectedResult == null ? null : selectedResult.chat();
        if (selected == null) {
            status.setText("Select a chat before exporting.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export selected chat");
        chooser.setInitialFileName(ChatMapViewBuilder.safeFileName(selected.title()) + ".md");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        java.io.File file = chooser.showSaveDialog(chatList.getScene().getWindow());
        if (file == null) {
            return;
        }
        backgroundActions.runValue("Exporting chat...", null,
                () -> controller.exportChatMarkdown(selected.id(), file.toPath()),
                exported -> status.setText(
                        exported ? "Exported " + selected.title() : "Selected chat no longer exists."));
    }

    private void importChatGptArchive() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import ChatGPT archive");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ChatGPT ZIP archive", "*.zip"));
        java.io.File file = chooser.showOpenDialog(chatList.getScene().getWindow());
        if (file == null) {
            return;
        }
        runInBackground("Importing ChatGPT archive...", null,
                () -> controller.importChatGptArchive(file.toPath()));
    }

    private void getLatestChat() {
        // Provider reads may block (live web/CDP fetch); run on the backend lane.
        runOnBackendLane("Importing available chat...", getLatestChatButton, controller::fetchLatestChat);
    }

    private void showConversationInventory() {
        // Queries every provider, including the web ones (live CDP fetch); backend lane.
        backgroundActions.runValueOnBackendLane("Discovering all discoverable conversations...", inventoryButton,
                controller::conversationInventory, this::showConversationInventoryDialog);
    }

    private void showConversationInventoryDialog(ConversationInventory inventory) {
        ChatMapDialogs.showConversationInventory(inventory, fontSizeState.current());
        status.setText("Conversation inventory loaded.");
    }

    private void summarizeSelectedChat() {
        Long chatId = selection.selectedChatId();
        if (chatId == null) {
            status.setText("Select a chat to summarize.");
            return;
        }
        // Blocking claude CLI call; run on the backend lane.
        runOnBackendLane("Summarizing chat " + chatId + "...", summarizeButton,
                () -> controller.summarizeAndTag(chatId));
    }

    /**
     * Runs a blocking controller call on a background thread, showing feedback: sets
     * the pending status and disables the triggering button while in flight, then
     * applies the resulting snapshot (or reports the error) back on the FX thread.
     */
    private void runInBackground(String pendingStatus, Button triggerButton, BackgroundCall call) {
        backgroundActions.runSnapshot(pendingStatus, triggerButton, call::run, snapshot -> {
            selection.applyListState(snapshot);
            selection.updateSelectionActionStates();
        }, exception -> {
            selection.updateSelectionActionStates();
            reportError(exception);
        });
    }

    /**
     * Same as {@link #runInBackground(String, Button, BackgroundCall)}, but for calls
     * that reach an AI backend or a live web/CDP provider fetch (can run for minutes)
     * rather than doing DB-only work. Runs on ChatMapRuntime's separate slow lane so
     * it never makes a search or chat-list load wait behind it.
     */
    private void runOnBackendLane(String pendingStatus, Button triggerButton, BackgroundCall call) {
        backgroundActions.runSnapshotOnBackendLane(pendingStatus, triggerButton, call::run, snapshot -> {
            selection.applyListState(snapshot);
            selection.updateSelectionActionStates();
        }, exception -> {
            selection.updateSelectionActionStates();
            reportError(exception);
        });
    }

    private void searchChats() {
        String query = searchField.getText();
        runInBackground("Searching...", null, () -> controller.searchChats(query));
        searchField.requestFocus();
        searchField.selectAll();
    }

    private void clearSearchAndFilters() {
        searchField.clear();
        projectChoice.getSelectionModel().clearSelection();
        tagChoice.getSelectionModel().clearSelection();
        runInBackground("Loading...", null, () -> controller.clearFilters());
        searchField.requestFocus();
    }

    private void createProject() {
        Optional<String> name = requestName("New project", "Project name");
        if (name.isEmpty()) {
            return;
        }
        backgroundActions.runValue(null, null,
                () -> controller.createProject(name.get()),
                created -> {
                    refreshOrganizationChoices();
                    projectChoice.getSelectionModel().select(created);
                    status.setText("Created project " + created.name());
                });
    }

    private void assignProject() {
        Long chatId = selection.selectedChatId();
        Project project = projectChoice.getValue();
        if (chatId == null || project == null) {
            status.setText("Select a chat and project.");
            return;
        }
        runInBackground("Assigning project...", null, () -> controller.assignProject(chatId, project.id()));
    }

    private void clearProject() {
        Long chatId = selection.selectedChatId();
        if (chatId == null) {
            status.setText("Select a chat.");
            return;
        }
        runInBackground("Clearing project...", null, () -> controller.clearProject(chatId));
    }

    private void filterByProject() {
        Project project = projectChoice.getValue();
        if (project == null) {
            status.setText("Select a project.");
            return;
        }
        runInBackground("Filtering...", null, () -> controller.filterByProject(project.id()));
    }

    private void createTag() {
        Optional<String> name = requestName("New tag", "Tag name");
        if (name.isEmpty()) {
            return;
        }
        backgroundActions.runValue(null, null,
                () -> controller.createTag(name.get()),
                created -> {
                    refreshOrganizationChoices();
                    tagChoice.getSelectionModel().select(created);
                    status.setText("Created tag " + created.name());
                });
    }

    private void addTag() {
        Long chatId = selection.selectedChatId();
        Tag tag = tagChoice.getValue();
        if (chatId == null || tag == null) {
            status.setText("Select a chat and tag.");
            return;
        }
        runInBackground("Adding tag...", null, () -> controller.addTag(chatId, tag.id()));
    }

    private void removeTag() {
        Long chatId = selection.selectedChatId();
        Tag tag = tagChoice.getValue();
        if (chatId == null || tag == null) {
            status.setText("Select a chat and tag.");
            return;
        }
        runInBackground("Removing tag...", null, () -> controller.removeTag(chatId, tag.id()));
    }

    private void filterByTag() {
        Tag tag = tagChoice.getValue();
        if (tag == null) {
            status.setText("Select a tag.");
            return;
        }
        runInBackground("Filtering...", null, () -> controller.filterByTag(tag.id()));
    }

    private void refreshOrganizationChoices() {
        backgroundActions.runValue(null, null,
                () -> new OrganizationChoices(controller.listProjects(), controller.listTags()),
                choices -> {
                    projectChoice.setItems(FXCollections.observableArrayList(choices.projects()));
                    tagChoice.setItems(FXCollections.observableArrayList(choices.tags()));
                });
    }

    private Optional<String> requestName(String title, String prompt) {
        return ChatMapDialogs.requestName(title, prompt);
    }

    private void reportError(Exception e) {
        status.setText("Error: " + e.getMessage());
        ChatMapDialogs.showError("Operation failed", e.getMessage());
    }

    private void applyFontSize(int size) {
        if (root != null) {
            root.setStyle("-fx-font-size: " + size + "pt;");
        }
        if (fontSizeChoice != null && !Integer.valueOf(size).equals(fontSizeChoice.getValue())) {
            fontSizeChoice.setValue(size);
        }
    }

    private void registerFontShortcuts(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN),
                () -> applyFontSize(fontSizeState.increase()));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ADD, KeyCombination.CONTROL_DOWN),
                () -> applyFontSize(fontSizeState.increase()));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN),
                () -> applyFontSize(fontSizeState.decrease()));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.SUBTRACT, KeyCombination.CONTROL_DOWN),
                () -> applyFontSize(fontSizeState.decrease()));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN),
                () -> applyFontSize(fontSizeState.reset()));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.NUMPAD0, KeyCombination.CONTROL_DOWN),
                () -> applyFontSize(fontSizeState.reset()));
    }

    @FunctionalInterface
    private interface BackgroundCall {
        ChatListState.Snapshot run() throws Exception;
    }

    /** Carrier for the two lists loaded together by {@link #refreshOrganizationChoices()}. */
    private record OrganizationChoices(List<Project> projects, List<Tag> tags) {
    }
}
