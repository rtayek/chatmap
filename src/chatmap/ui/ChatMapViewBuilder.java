package chatmap.ui;

import java.util.List;
import java.util.function.Consumer;

import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Helper methods for constructing JavaFX nodes, formatting search results, and converters. */
public final class ChatMapViewBuilder {

    public static final String COMPACT_BUTTON_STYLE = "-fx-padding: 3 7 3 7;";

    private ChatMapViewBuilder() {
    }

    /** An action that may fail; run through {@link #button} it reports the failure instead of crashing the FX thread. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Widgets from {@link #createToolbar} the caller must keep live references to. */
    public record ToolbarWidgets(
            ToolBar toolBar,
            Button exportChatButton,
            Button getLatestChatButton,
            Button inventoryButton,
            Button summarizeButton,
            ComboBox<Integer> fontSizeChoice) {
    }

    /** Widgets from {@link #createSearchBar} the caller must keep live references to. */
    public record SearchBarWidgets(HBox searchBar, TextField searchField) {
    }

    /** Widgets from {@link #createProjectBar} the caller must keep live references to. */
    public record ProjectBarWidgets(HBox projectBar, ComboBox<Project> projectChoice) {
    }

    /** Widgets from {@link #createTagBar} the caller must keep live references to. */
    public record TagBarWidgets(HBox tagBar, ComboBox<Tag> tagChoice) {
    }

    public static Button button(String text, ThrowingRunnable action, Consumer<Exception> errorHandler) {
        Button button = new Button(text);
        button.setStyle(COMPACT_BUTTON_STYLE);
        button.setOnAction(event -> {
            event.consume();
            runWithFeedback(action, errorHandler);
        });
        return button;
    }

    /** Runs {@code action}, reporting a failure to {@code errorHandler} instead of throwing. */
    public static void runWithFeedback(ThrowingRunnable action, Consumer<Exception> errorHandler) {
        try {
            action.run();
        } catch (Exception e) {
            errorHandler.accept(e);
        }
    }

    /** The main toolbar: import/export/live-fetch/inventory/summarize actions plus the font-size choice. */
    public static ToolbarWidgets createToolbar(
            int initialFontSize,
            ThrowingRunnable onImportText,
            ThrowingRunnable onImportMarkdown,
            ThrowingRunnable onImportChatGptJson,
            ThrowingRunnable onImportChatGptArchive,
            ThrowingRunnable onExportChat,
            ThrowingRunnable onGetLatestChat,
            ThrowingRunnable onShowInventory,
            ThrowingRunnable onSummarize,
            Consumer<Integer> onFontSizeSelected,
            Consumer<Exception> errorHandler) {
        Button exportChatButton = button("Export Chat Markdown", onExportChat, errorHandler);
        exportChatButton.setDisable(true);
        Button getLatestChatButton = button("Import available chat", onGetLatestChat, errorHandler);
        Button inventoryButton = button("Conversation Inventory", onShowInventory, errorHandler);
        Button summarizeButton = button("Summarize & tag", onSummarize, errorHandler);
        summarizeButton.setDisable(true);

        ComboBox<Integer> fontSizeChoice = new ComboBox<>(FXCollections.observableArrayList(FontSizeState.SIZES));
        fontSizeChoice.setValue(initialFontSize);
        fontSizeChoice.setOnAction(actionEvent -> {
            Integer selectedSize = fontSizeChoice.getValue();
            if (selectedSize != null) {
                onFontSizeSelected.accept(selectedSize);
            }
        });

        ToolBar toolBar = new ToolBar(
                button("Import Text", onImportText, errorHandler),
                button("Import Markdown", onImportMarkdown, errorHandler),
                button("Import ChatGPT JSON", onImportChatGptJson, errorHandler),
                button("Import ChatGPT Archive", onImportChatGptArchive, errorHandler),
                exportChatButton,
                getLatestChatButton,
                inventoryButton,
                summarizeButton,
                new Label("Font"),
                fontSizeChoice);

        return new ToolbarWidgets(toolBar, exportChatButton, getLatestChatButton, inventoryButton,
                summarizeButton, fontSizeChoice);
    }

    /** The search bar: a text field (Enter triggers search) plus explicit Search/Clear buttons. */
    public static SearchBarWidgets createSearchBar(ThrowingRunnable onSearch, ThrowingRunnable onClear,
            Consumer<Exception> errorHandler) {
        TextField searchField = new TextField();
        searchField.setPromptText("Search message text");
        searchField.setOnAction(actionEvent -> {
            actionEvent.consume();
            runWithFeedback(onSearch, errorHandler);
        });
        HBox searchBar = new HBox(8,
                searchField,
                button("Search", onSearch, errorHandler),
                button("Clear", onClear, errorHandler));
        return new SearchBarWidgets(searchBar, searchField);
    }

    /** The project bar: project choice plus new/assign/clear/filter actions. */
    public static ProjectBarWidgets createProjectBar(ThrowingRunnable onNew, ThrowingRunnable onAssign,
            ThrowingRunnable onClear, ThrowingRunnable onFilter, Consumer<Exception> errorHandler) {
        ComboBox<Project> projectChoice = new ComboBox<>();
        projectChoice.setPromptText("Project");
        projectChoice.setConverter(namedProjectConverter());
        HBox projectBar = new HBox(8,
                new Label("Project"),
                projectChoice,
                button("New", onNew, errorHandler),
                button("Assign", onAssign, errorHandler),
                button("Clear Project", onClear, errorHandler),
                button("Filter", onFilter, errorHandler));
        return new ProjectBarWidgets(projectBar, projectChoice);
    }

    /** The tag bar: tag choice plus new/add/remove/filter actions and a filters-wide clear. */
    public static TagBarWidgets createTagBar(ThrowingRunnable onNew, ThrowingRunnable onAdd,
            ThrowingRunnable onRemove, ThrowingRunnable onFilter, ThrowingRunnable onClearFilters,
            Consumer<Exception> errorHandler) {
        ComboBox<Tag> tagChoice = new ComboBox<>();
        tagChoice.setPromptText("Tag");
        tagChoice.setConverter(namedTagConverter());
        HBox tagBar = new HBox(8,
                new Label("Tag"),
                tagChoice,
                button("New", onNew, errorHandler),
                button("Add", onAdd, errorHandler),
                button("Remove", onRemove, errorHandler),
                button("Filter", onFilter, errorHandler),
                button("Clear Filters", onClearFilters, errorHandler));
        return new TagBarWidgets(tagBar, tagChoice);
    }

    public static ListView<SearchResult> createChatListView(ChangeListener<SearchResult> selectionListener) {
        ListView<SearchResult> list = new ListView<>();
        list.setMinWidth(180);
        list.setCellFactory(chatListView -> new ListCell<>() {
            @Override
            protected void updateItem(SearchResult result, boolean empty) {
                super.updateItem(result, empty);
                setText(empty || result == null ? null : formatResultRow(result));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener(selectionListener);
        return list;
    }

    public static TextArea createDetailTextArea() {
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMinWidth(300);
        return textArea;
    }

    public static BorderPane assembleRootPane(Node toolbar, Node searchBar, Node projectBar, Node tagBar,
            Node content, Node status) {
        BorderPane pane = new BorderPane();
        pane.setTop(new VBox(6, toolbar, searchBar, projectBar, tagBar));
        pane.setCenter(content);
        pane.setBottom(new VBox(status));
        BorderPane.setMargin(searchBar, new Insets(8, 8, 0, 8));
        BorderPane.setMargin(projectBar, new Insets(0, 8, 0, 8));
        BorderPane.setMargin(tagBar, new Insets(0, 8, 0, 8));
        BorderPane.setMargin(content, new Insets(8));
        BorderPane.setMargin(status, new Insets(4, 8, 8, 8));
        return pane;
    }

    public static String formatResultRow(SearchResult result) {
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

    public static String formatTags(List<Tag> tags) {
        return tags.stream()
                .map(Tag::name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    public static String safeFileName(String title) {
        String cleaned = title.replaceAll("[^A-Za-z0-9.-]+", "-").replaceAll("^-|-$", "");
        return cleaned.isBlank() ? "chat" : cleaned;
    }

    public static StringConverter<Project> namedProjectConverter() {
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

    public static StringConverter<Tag> namedTagConverter() {
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
}
