package chatmap.ui;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import chatmap.domain.Chat;
import chatmap.domain.Message;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import chatmap.exporter.ChatExportModel;
import chatmap.service.ExportService;
import chatmap.service.ImportService;
import chatmap.service.SearchService;
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
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Minimal JavaFX list/detail UI for file import and selected-chat Markdown export. */
public final class ChatMapApp extends Application {

    private Connection conn;
    private ImportService importService;
    private ExportService exportService;
    private SearchService searchService;
    private ListView<SearchResult> chatList;
    private TextArea detail;
    private TextField searchField;
    private Label status;

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
        importService = new ImportService(chats, messages);
        exportService = new ExportService(chats, messages, projects, tags);
        searchService = new SearchService(search);

        chatList = new ListView<>();
        chatList.setCellFactory(chatListView -> new ListCell<>() {
            @Override
            protected void updateItem(SearchResult result, boolean empty) {
                super.updateItem(result, empty);
                setText(empty || result == null ? null : formatResultRow(result));
            }
        });
        chatList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previousResult, selectedResult) -> showChat(
                        selectedResult == null ? null : selectedResult.chat()));

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

        ToolBar toolbar = new ToolBar(
                button("Import Text", () -> importFile("Import text", "*.txt")),
                button("Import Markdown", () -> importFile("Import Markdown", "*.md", "*.markdown")),
                button("Import ChatGPT JSON", () -> importFile("Import ChatGPT JSON", "*.json")),
                button("Export Chat Markdown", this::exportSelectedChat));
        HBox searchBar = new HBox(8,
                searchField,
                button("Search", this::searchChats),
                button("Clear", this::clearSearch));

        BorderPane root = new BorderPane();
        root.setTop(new VBox(toolbar, searchBar));
        root.setLeft(chatList);
        root.setCenter(detail);
        root.setBottom(new VBox(status));
        BorderPane.setMargin(searchBar, new Insets(8, 8, 0, 8));
        BorderPane.setMargin(chatList, new Insets(8));
        BorderPane.setMargin(detail, new Insets(8));
        BorderPane.setMargin(status, new Insets(4, 8, 8, 8));

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
        Chat imported = importService.importFile(file.toPath());
        refreshChats();
        selectChat(imported.id());
        status.setText("Imported " + imported.title());
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
        boolean exported = exportService.writeChatMarkdown(selected.id(), file.toPath());
        status.setText(exported ? "Exported " + selected.title() : "Selected chat no longer exists.");
    }

    private void searchChats() throws Exception {
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            clearSearch();
            return;
        }
        List<SearchResult> matches = searchService.searchResults(query);
        setChatItems(matches, matches.size() == 1);
        status.setText(formatMatchStatus(matches.size()));
        searchField.requestFocus();
        searchField.selectAll();
    }

    private void clearSearch() throws Exception {
        searchField.clear();
        refreshChats();
        detail.clear();
        status.setText("Showing all chats");
        searchField.requestFocus();
    }

    private void showChat(Chat chat) {
        runWithFeedback(() -> {
            if (chat == null) {
                detail.clear();
                return;
            }
            ChatExportModel model = exportService.loadChat(chat.id()).orElse(null);
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
        setChatItems(searchService.searchResults(""), false);
    }

    private void setChatItems(List<SearchResult> items, boolean autoSelectSingle) {
        chatList.getSelectionModel().clearSelection();
        chatList.setItems(FXCollections.observableArrayList(items));
        if (autoSelectSingle && items.size() == 1) {
            selectChat(items.getFirst().chatId());
        } else {
            detail.clear();
        }
    }

    private void selectChat(long chatId) {
        for (SearchResult result : chatList.getItems()) {
            if (result.chatId() == chatId) {
                chatList.getSelectionModel().select(result);
                return;
            }
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

    private static String formatMatchStatus(int matches) {
        if (matches == 0) {
            return "No matches";
        }
        if (matches == 1) {
            return "1 match";
        }
        return matches + " matches";
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
