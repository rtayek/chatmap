package chatmap.ui;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import chatmap.exporter.ChatExportModel;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.service.ProjectService;
import chatmap.service.SearchService;
import chatmap.service.TagService;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.MessageRepository;
import chatmap.storage.ProjectRepository;
import chatmap.storage.SearchRepository;
import chatmap.storage.TagRepository;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/** Minimal JavaFX list/detail UI for file import and selected-chat Markdown export. */
public final class ChatMapApp extends Application {

    private Connection conn;
    private ChatMapController controller;
    private ListView<SearchResult> chatList;
    private TextArea detail;
    private TextField searchField;
    private ComboBox<Project> projectChoice;
    private ComboBox<Tag> tagChoice;
    private Button exportChatButton;
    private Label status;
    private boolean applyingListState;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Path dbPath = Path.of(System.getProperty("user.home"), ".chatmap", "chatmap.db");
        dbPath.toFile().getParentFile().mkdirs();
        conn = new Database("jdbc:sqlite:" + dbPath).openAndInitialize();
        ChatRepository chats = new ChatRepository(conn);
        MessageRepository messages = new MessageRepository(conn);
        ProjectRepository projects = new ProjectRepository(conn);
        TagRepository tags = new TagRepository(conn);
        SearchRepository search = new SearchRepository(conn);
        controller = new ChatMapController(
                new ImportService(chats, messages),
                new ExportService(chats, messages, projects, tags),
                new SearchService(search),
                new ProjectService(projects, chats),
                new TagService(tags, chats));

        chatList = new ListView<>();
        chatList.setCellFactory(chatListView -> new ListCell<>() {
            @Override
            protected void updateItem(SearchResult result, boolean empty) {
                super.updateItem(result, empty);
                setText(empty || result == null ? null : formatResultRow(result));
            }
        });
        chatList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previousResult, selectedResult) -> handleSelectedResult(selectedResult));

        detail = new TextArea();
        detail.setEditable(false);
        detail.setWrapText(true);
        searchField = new TextField();
        searchField.setPromptText("Search message text");
        searchField.setOnAction(actionEvent -> {
            actionEvent.consume();
            runWithFeedback(this::searchChats);
        });
        status = new Label("Ready");
        exportChatButton = button("Export Chat Markdown", this::exportSelectedChat);
        exportChatButton.setDisable(true);

        ToolBar toolbar = new ToolBar(
                button("Import Text", () -> importFile("Import text", "*.txt")),
                button("Import Markdown", () -> importFile("Import Markdown", "*.md", "*.markdown")),
                button("Import ChatGPT JSON", () -> importFile("Import ChatGPT JSON", "*.json")),
                exportChatButton);
        HBox searchBar = new HBox(8,
                searchField,
                button("Search", this::searchChats),
                button("Clear", this::clearSearchAndFilters));
        projectChoice = new ComboBox<>();
        projectChoice.setPromptText("Project");
        projectChoice.setConverter(namedProjectConverter());
        HBox projectBar = new HBox(8,
                new Label("Project"),
                projectChoice,
                button("New", this::createProject),
                button("Assign", this::assignProject),
                button("Clear Project", this::clearProject),
                button("Filter", this::filterByProject));
        tagChoice = new ComboBox<>();
        tagChoice.setPromptText("Tag");
        tagChoice.setConverter(namedTagConverter());
        HBox tagBar = new HBox(8,
                new Label("Tag"),
                tagChoice,
                button("New", this::createTag),
                button("Add", this::addTag),
                button("Remove", this::removeTag),
                button("Filter", this::filterByTag),
                button("Clear Filters", this::clearSearchAndFilters));

        BorderPane root = new BorderPane();
        root.setTop(new VBox(6, toolbar, searchBar, projectBar, tagBar));
        root.setLeft(chatList);
        root.setCenter(detail);
        root.setBottom(new VBox(status));
        BorderPane.setMargin(searchBar, new Insets(8, 8, 0, 8));
        BorderPane.setMargin(projectBar, new Insets(0, 8, 0, 8));
        BorderPane.setMargin(tagBar, new Insets(0, 8, 0, 8));
        BorderPane.setMargin(chatList, new Insets(8));
        BorderPane.setMargin(detail, new Insets(8));
        BorderPane.setMargin(status, new Insets(4, 8, 8, 8));

        refreshOrganizationChoices();
        refreshChats();
        stage.setTitle("ChatMap");
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    private Button button(String text, ThrowingRunnable action) {
        Button button = new Button(text);
        button.setOnAction(actionEvent -> {
            actionEvent.consume();
            runWithFeedback(action);
        });
        return button;
    }

    private void importFile(String title, String... patterns) throws Exception {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(title, patterns));
        java.io.File file = chooser.showOpenDialog(chatList.getScene().getWindow());
        if (file == null) {
            return;
        }
        applyListState(controller.importFile(file.toPath()));
    }

    private void exportSelectedChat() throws Exception {
        SearchResult selectedResult = chatList.getSelectionModel().getSelectedItem();
        Chat selected = selectedResult == null ? null : selectedResult.chat();
        if (selected == null) {
            status.setText("Select a chat before exporting.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export selected chat");
        chooser.setInitialFileName(safeFileName(selected.title()) + ".md");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        java.io.File file = chooser.showSaveDialog(chatList.getScene().getWindow());
        if (file == null) {
            return;
        }
        boolean exported = controller.exportChatMarkdown(selected.id(), file.toPath());
        status.setText(exported ? "Exported " + selected.title() : "Selected chat no longer exists.");
    }

    private void searchChats() throws Exception {
        String query = searchField.getText();
        applyListState(controller.searchChats(query));
        searchField.requestFocus();
        searchField.selectAll();
    }

    private void clearSearchAndFilters() throws Exception {
        searchField.clear();
        projectChoice.getSelectionModel().clearSelection();
        tagChoice.getSelectionModel().clearSelection();
        applyListState(controller.clearFilters());
        searchField.requestFocus();
    }

    private void createProject() throws Exception {
        Optional<String> name = requestName("New project", "Project name");
        if (name.isEmpty()) {
            return;
        }
        Project created = controller.createProject(name.get());
        refreshOrganizationChoices();
        projectChoice.getSelectionModel().select(created);
        status.setText("Created project " + created.name());
    }

    private void assignProject() throws Exception {
        Long chatId = selectedChatId();
        Project project = projectChoice.getValue();
        if (chatId == null || project == null) {
            status.setText("Select a chat and project.");
            return;
        }
        applyListState(controller.assignProject(chatId, project.id()));
    }

    private void clearProject() throws Exception {
        Long chatId = selectedChatId();
        if (chatId == null) {
            status.setText("Select a chat.");
            return;
        }
        applyListState(controller.clearProject(chatId));
    }

    private void filterByProject() throws Exception {
        Project project = projectChoice.getValue();
        if (project == null) {
            status.setText("Select a project.");
            return;
        }
        applyListState(controller.filterByProject(project.id()));
    }

    private void createTag() throws Exception {
        Optional<String> name = requestName("New tag", "Tag name");
        if (name.isEmpty()) {
            return;
        }
        Tag created = controller.createTag(name.get());
        refreshOrganizationChoices();
        tagChoice.getSelectionModel().select(created);
        status.setText("Created tag " + created.name());
    }

    private void addTag() throws Exception {
        Long chatId = selectedChatId();
        Tag tag = tagChoice.getValue();
        if (chatId == null || tag == null) {
            status.setText("Select a chat and tag.");
            return;
        }
        applyListState(controller.addTag(chatId, tag.id()));
    }

    private void removeTag() throws Exception {
        Long chatId = selectedChatId();
        Tag tag = tagChoice.getValue();
        if (chatId == null || tag == null) {
            status.setText("Select a chat and tag.");
            return;
        }
        applyListState(controller.removeTag(chatId, tag.id()));
    }

    private void filterByTag() throws Exception {
        Tag tag = tagChoice.getValue();
        if (tag == null) {
            status.setText("Select a tag.");
            return;
        }
        applyListState(controller.filterByTag(tag.id()));
    }

    private void refreshOrganizationChoices() throws Exception {
        projectChoice.setItems(FXCollections.observableArrayList(controller.listProjects()));
        tagChoice.setItems(FXCollections.observableArrayList(controller.listTags()));
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
        updateExportActionState();
        if (selectedResult == null) {
            detail.clear();
            return;
        }
        controller.selectChat(selectedResult.chatId());
        showChatDetails(selectedResult.chatId());
    }

    private void showChatDetails(long chatId) {
        runWithFeedback(() -> {
            ChatExportModel model = controller.loadChatDetails(chatId).orElse(null);
            if (model == null) {
                detail.clear();
                status.setText("Selected chat no longer exists.");
                return;
            }
            StringBuilder out = new StringBuilder();
            out.append(model.chat().title()).append("\n");
            out.append("Source: ").append(model.chat().source().dbValue()).append("\n");
            out.append("Imported: ").append(model.chat().importedAt()).append("\n\n");
            for (Message message : model.messages()) {
                out.append("[").append(message.role()).append("]\n");
                out.append(message.text()).append("\n\n");
            }
            detail.setText(out.toString());
        });
    }

    private void refreshChats() throws Exception {
        applyListState(controller.loadAllChats());
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
            updateExportActionState();
        } else if (!selectChat(snapshot.selectedChatId())) {
            detail.clear();
            updateExportActionState();
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

    private void updateExportActionState() {
        if (exportChatButton != null) {
            exportChatButton.setDisable(chatList.getSelectionModel().getSelectedItem() == null);
        }
    }

    private void runWithFeedback(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception e) {
            status.setText("Error: " + e.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("ChatMap");
            alert.setHeaderText("Operation failed");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    private static String safeFileName(String title) {
        String cleaned = title.replaceAll("[^A-Za-z0-9.-]+", "-").replaceAll("^-|-$", "");
        return cleaned.isBlank() ? "chat" : cleaned;
    }

    private static String formatResultRow(SearchResult result) {
        StringBuilder row = new StringBuilder();
        row.append(result.chat().title()).append("\n");
        row.append("Source: ").append(result.chat().source().dbValue());
        if (result.projectName() != null && !result.projectName().isBlank()) {
            row.append(" | Project: ").append(result.projectName());
        }
        if (!result.tags().isEmpty()) {
            row.append(" | Tags: ").append(formatTags(result.tags()));
        }
        if (result.snippet() != null && !result.snippet().isBlank()) {
            row.append("\n").append(result.snippet());
        }
        return row.toString();
    }

    private static String formatTags(List<Tag> tags) {
        return tags.stream()
                .map(Tag::name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static StringConverter<Project> namedProjectConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Project project) {
                return project == null ? "" : project.name();
            }

            @Override
            public Project fromString(String text) {
                return null;
            }
        };
    }

    private static StringConverter<Tag> namedTagConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Tag tag) {
                return tag == null ? "" : tag.name();
            }

            @Override
            public Tag fromString(String text) {
                return null;
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
