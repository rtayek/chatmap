package chatmap.ui;

import java.nio.file.Files;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import chatmap.config.ChatMapPaths;
import chatmap.config.ChatMapPaths.ParsedArguments;
import chatmap.config.ChatMapPaths.ResolvedPaths;
import chatmap.domain.Chat;
import chatmap.domain.ChatSummary;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import chatmap.exporter.ChatExportModel;
import chatmap.storage.Database;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Minimal JavaFX list/detail UI for file import and selected-chat Markdown export. */
public final class ChatMapApp extends Application {
    private static final String COMPACT_BUTTON_STYLE = "-fx-padding: 3 7 3 7;";

    private Connection conn;
    private ChatMapController controller;
    private ListView<SearchResult> chatList;
    private TextArea detail;
    private TextField searchField;
    private ComboBox<Project> projectChoice;
    private ComboBox<Tag> tagChoice;
    private Button exportChatButton;
    private Button getLatestChatButton;
    private Button summarizeButton;
    private ComboBox<Integer> fontSizeChoice;
    private FontSizeState fontSizeState;
    private BorderPane root;
    private Label status;
    private boolean applyingListState;
    private SerializedTaskExecutor backgroundExecutor;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        ParsedArguments parsedArguments = ChatMapPaths.parse(getParameters().getRaw());
        if (!parsedArguments.remainingArgs().isEmpty()) {
            throw new IllegalArgumentException("Usage: ChatMap [--home <directory>]");
        }
        ResolvedPaths paths = parsedArguments.paths();
        System.out.println(ChatMapPaths.diagnostics(paths));
        Files.createDirectories(paths.homeDirectory());

        conn = new Database("jdbc:sqlite:" + paths.databasePath()).openAndInitialize();
        backgroundExecutor = new SerializedTaskExecutor("chatmap-background");
        controller = ChatMapControllerFactory.create(conn);

        fontSizeState = new FontSizeState();
        status = new Label("Ready");
        chatList = ChatMapViewBuilder.createChatListView(
                (observable, previousResult, selectedResult) -> handleSelectedResult(selectedResult));
        detail = ChatMapViewBuilder.createDetailTextArea();

        ToolBar toolbar = createToolbar();
        HBox searchBar = createSearchBar();
        HBox projectBar = createProjectBar();
        HBox tagBar = createTagBar();

        SplitPane content = new SplitPane(chatList, detail);
        content.setDividerPositions(0.32);
        root = ChatMapViewBuilder.assembleRootPane(toolbar, searchBar, projectBar, tagBar, content, status);

        refreshOrganizationChoices();
        runInBackground("Loading chats...", null, () -> controller.loadAllChats());
        stage.setTitle("ChatMap");
        applyFontSize(fontSizeState.current());
        Scene scene = new Scene(root, 900, 600);
        registerFontShortcuts(scene);
        stage.setScene(scene);
        stage.show();
    }





    private ToolBar createToolbar() {
        exportChatButton = button("Export Chat Markdown", this::exportSelectedChat);
        exportChatButton.setDisable(true);
        getLatestChatButton = button("Import available chat", this::getLatestChat);
        summarizeButton = button("Summarize & tag", this::summarizeSelectedChat);
        summarizeButton.setDisable(true);

        fontSizeChoice = new ComboBox<>(FXCollections.observableArrayList(FontSizeState.SIZES));
        fontSizeChoice.setValue(fontSizeState.current());
        fontSizeChoice.setOnAction(actionEvent -> {
            Integer selectedSize = fontSizeChoice.getValue();
            if (selectedSize != null) {
                applyFontSize(fontSizeState.set(selectedSize));
            }
        });

        return new ToolBar(
                button("Import Text", () -> importFile("Import text", "*.txt")),
                button("Import Markdown", () -> importFile("Import Markdown", "*.md", "*.markdown")),
                button("Import ChatGPT JSON", () -> importFile("Import ChatGPT JSON", "*.json")),
                button("Import ChatGPT Archive", this::importChatGptArchive),
                exportChatButton,
                getLatestChatButton,
                summarizeButton,
                new Label("Font"),
                fontSizeChoice);
    }

    private HBox createSearchBar() {
        searchField = new TextField();
        searchField.setPromptText("Search message text");
        searchField.setOnAction(actionEvent -> {
            actionEvent.consume();
            runWithFeedback(this::searchChats);
        });
        return new HBox(8,
                searchField,
                button("Search", this::searchChats),
                button("Clear", this::clearSearchAndFilters));
    }

    private HBox createProjectBar() {
        projectChoice = new ComboBox<>();
        projectChoice.setPromptText("Project");
        projectChoice.setConverter(ChatMapViewBuilder.namedProjectConverter());
        return new HBox(8,
                new Label("Project"),
                projectChoice,
                button("New", this::createProject),
                button("Assign", this::assignProject),
                button("Clear Project", this::clearProject),
                button("Filter", this::filterByProject));
    }

    private HBox createTagBar() {
        tagChoice = new ComboBox<>();
        tagChoice.setPromptText("Tag");
        tagChoice.setConverter(ChatMapViewBuilder.namedTagConverter());
        return new HBox(8,
                new Label("Tag"),
                tagChoice,
                button("New", this::createTag),
                button("Add", this::addTag),
                button("Remove", this::removeTag),
                button("Filter", this::filterByTag),
                button("Clear Filters", this::clearSearchAndFilters));
    }



    @Override
    public void stop() throws Exception {
        // Close the connection only once the background worker has actually stopped
        // using it. If a long task (e.g. a summarize CLI call or a large archive
        // import) has not drained in time, closing the connection under it would fail
        // mid-operation, so leave it for the JVM to release on exit -- WAL keeps the
        // database consistent.
        boolean workerStopped = backgroundExecutor == null
                || backgroundExecutor.shutdownAndAwait(Duration.ofSeconds(5));
        if (conn != null) {
            if (workerStopped) {
                conn.close();
            } else {
                System.err.println("[ChatMap] Background work did not stop in time; "
                        + "leaving the database connection for the JVM to release on exit.");
            }
        }
    }

    private Button button(String text, ThrowingRunnable action) {
        Button button = new Button(text);
        button.setStyle(COMPACT_BUTTON_STYLE);
        button.setOnAction(actionEvent -> {
            actionEvent.consume();
            runWithFeedback(action);
        });
        return button;
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
        status.setText("Exporting chat...");
        backgroundExecutor.submit(() -> {
            try {
                boolean exported = controller.exportChatMarkdown(selected.id(), file.toPath());
                Platform.runLater(() -> status.setText(exported ? "Exported " + selected.title() : "Selected chat no longer exists."));
            } catch (Exception e) {
                Platform.runLater(() -> reportError(e));
            }
        });
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
        // Provider reads may block; run off the FX thread so the UI stays responsive.
        runInBackground("Importing available chat...", getLatestChatButton, controller::fetchLatestChat);
    }

    private void summarizeSelectedChat() {
        Long chatId = selectedChatId();
        if (chatId == null) {
            status.setText("Select a chat to summarize.");
            return;
        }
        // Blocking claude CLI call; run off the FX thread.
        runInBackground("Summarizing chat " + chatId + "...", summarizeButton,
                () -> controller.summarizeAndTag(chatId));
    }

    /**
     * Runs a blocking controller call on a background thread, showing feedback: sets
     * the pending status and disables the triggering button while in flight, then
     * applies the resulting snapshot (or reports the error) back on the FX thread.
     */
    private void runInBackground(String pendingStatus, Button triggerButton, BackgroundCall call) {
        status.setText(pendingStatus);
        if (triggerButton != null) {
            triggerButton.setDisable(true);
        }
        backgroundExecutor.submit(() -> {
            try {
                ChatListState.Snapshot snapshot = call.run();
                Platform.runLater(() -> {
                    applyListState(snapshot);
                    updateSelectionActionStates();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateSelectionActionStates();
                    reportError(e);
                });
            }
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
        backgroundExecutor.submit(() -> {
            try {
                Project created = controller.createProject(name.get());
                Platform.runLater(() -> {
                    refreshOrganizationChoices();
                    projectChoice.getSelectionModel().select(created);
                    status.setText("Created project " + created.name());
                });
            } catch (Exception e) {
                Platform.runLater(() -> reportError(e));
            }
        });
    }

    private void assignProject() {
        Long chatId = selectedChatId();
        Project project = projectChoice.getValue();
        if (chatId == null || project == null) {
            status.setText("Select a chat and project.");
            return;
        }
        runInBackground("Assigning project...", null, () -> controller.assignProject(chatId, project.id()));
    }

    private void clearProject() {
        Long chatId = selectedChatId();
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
        backgroundExecutor.submit(() -> {
            try {
                Tag created = controller.createTag(name.get());
                Platform.runLater(() -> {
                    refreshOrganizationChoices();
                    tagChoice.getSelectionModel().select(created);
                    status.setText("Created tag " + created.name());
                });
            } catch (Exception e) {
                Platform.runLater(() -> reportError(e));
            }
        });
    }

    private void addTag() {
        Long chatId = selectedChatId();
        Tag tag = tagChoice.getValue();
        if (chatId == null || tag == null) {
            status.setText("Select a chat and tag.");
            return;
        }
        runInBackground("Adding tag...", null, () -> controller.addTag(chatId, tag.id()));
    }

    private void removeTag() {
        Long chatId = selectedChatId();
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
        backgroundExecutor.submit(() -> {
            try {
                List<Project> projects = controller.listProjects();
                List<Tag> tags = controller.listTags();
                Platform.runLater(() -> {
                    projectChoice.setItems(FXCollections.observableArrayList(projects));
                    tagChoice.setItems(FXCollections.observableArrayList(tags));
                });
            } catch (Exception e) {
                Platform.runLater(() -> reportError(e));
            }
        });
    }

    private Long selectedChatId() {
        SearchResult selected = chatList.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.chatId();
    }

    private Optional<String> requestName(String title, String prompt) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        return dialog.showAndWait();
    }

    private void handleSelectedResult(SearchResult selectedResult) {
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
        backgroundExecutor.submit(() -> {
            try {
                // Record the selection and load details on the executor, never on the FX
                // thread: the controller lock can be held for minutes by a summarize
                // (claude CLI) or live fetch, so touching it on the FX thread would
                // freeze the UI. Here it just queues behind that work instead.
                controller.selectChat(chatId);
                ChatExportModel model = controller.loadChatDetails(chatId).orElse(null);
                ChatSummary summary = model == null ? null : controller.latestSummary(chatId).orElse(null);
                Platform.runLater(() -> {
                    if (model == null) {
                        detail.clear();
                        status.setText("Selected chat no longer exists.");
                        return;
                    }
                    StringBuilder out = new StringBuilder();
                    out.append(model.chat().title()).append("\n");
                    out.append("Source: ").append(model.chat().source().dbValue()).append("\n");
                    out.append("Imported: ").append(model.chat().importedAt()).append("\n\n");
                    if (summary != null) {
                        out.append("AI Summary (").append(summary.generatedBy()).append("): ")
                                .append(summary.summary()).append("\n\n");
                    }
                    for (Message message : model.messages()) {
                        out.append("[").append(message.role()).append("]\n");
                        out.append(message.text()).append("\n\n");
                    }
                    detail.setText(out.toString());
                });
            } catch (Exception e) {
                Platform.runLater(() -> reportError(e));
            }
        });
    }



    private void applyListState(ChatListState.Snapshot snapshot) {
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

    private void updateSelectionActionStates() {
        boolean noSelection = chatList.getSelectionModel().getSelectedItem() == null;
        if (exportChatButton != null) {
            exportChatButton.setDisable(noSelection);
        }
        if (summarizeButton != null) {
            summarizeButton.setDisable(noSelection);
        }
    }

    private void runWithFeedback(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception e) {
            reportError(e);
        }
    }

    private void reportError(Exception e) {
        status.setText("Error: " + e.getMessage());
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ChatMap");
        alert.setHeaderText("Operation failed");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
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
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface BackgroundCall {
        ChatListState.Snapshot run() throws Exception;
    }
}
