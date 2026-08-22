package chatmap.presentation.ui;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import chatmap.domain.Chat;
import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Helper methods for constructing JavaFX nodes, formatting search results, and converters. */
public final class ChatMapViewBuilder {

    private static final int CONTROL_GAP = 8;
    private static final int SECTION_GAP = 4;
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
            MenuBar menuBar,
            MenuItem exportChatItem,
            MenuItem getLatestChatItem,
            MenuItem inventoryItem,
            MenuItem summarizeItem,
            Consumer<Integer> selectFontSize,
            CheckMenuItem searchBarToggle,
            CheckMenuItem projectBarToggle,
            CheckMenuItem relatedProjectBarToggle,
            CheckMenuItem tagBarToggle) {
    }

    /** Widgets from {@link #createSearchBar} the caller must keep live references to. */
    public record SearchBarWidgets(HBox searchBar, TextField searchField) {
    }

    /** Widgets from {@link #createProjectBar} the caller must keep live references to. */
    public record ProjectBarWidgets(HBox projectBar, ComboBox<Project> projectChoice) {
    }

    /** Widgets from {@link #createRelatedProjectBar} the caller must keep live references to. */
    public record RelatedProjectBarWidgets(HBox relatedProjectBar, ComboBox<Project> relatedProjectChoice) {
    }

    /** Widgets from {@link #createTagBar} the caller must keep live references to. */
    public record TagBarWidgets(HBox tagBar, ComboBox<Tag> tagChoice) {
    }

    /** Widgets from {@link #createPromptPane} for routed prompt execution. */
    public record PromptPaneWidgets(
            VBox promptPane,
            ComboBox<Project> projectChoice,
            TextField conversationField,
            ComboBox<Chat> resumeChatChoice,
            TextArea historyArea,
            TextArea promptArea,
            Button sendButton,
            Label classificationLabel,
            Label routeLabel,
            TextArea responseArea) {
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

    private static MenuItem menuItem(String text, ThrowingRunnable action, Consumer<Exception> errorHandler) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> runWithFeedback(action, errorHandler));
        return item;
    }

    private static CheckMenuItem barVisibilityToggle(String text) {
        CheckMenuItem item = new CheckMenuItem(text);
        item.setSelected(true);
        return item;
    }

    /** The menu bar: File/View/Tools/Help, replacing the always-visible button toolbar. */
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
            ThrowingRunnable onExit,
            Consumer<Integer> onFontSizeSelected,
            Consumer<Exception> errorHandler) {
        MenuItem exportChatItem = menuItem("Export Chat", onExportChat, errorHandler);
        exportChatItem.setDisable(true);
        MenuItem getLatestChatItem = menuItem("Get Latest Chat", onGetLatestChat, errorHandler);
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(event -> runWithFeedback(onExit, errorHandler));

        Menu fileMenu = new Menu("File",
                null,
                menuItem("Import Text", onImportText, errorHandler),
                menuItem("Import Markdown", onImportMarkdown, errorHandler),
                menuItem("Import ChatGPT JSON", onImportChatGptJson, errorHandler),
                menuItem("Import ChatGPT Archive", onImportChatGptArchive, errorHandler),
                exportChatItem,
                getLatestChatItem,
                new SeparatorMenuItem(),
                exitItem);

        ToggleGroup fontSizeGroup = new ToggleGroup();
        Map<Integer, RadioMenuItem> fontSizeItems = new java.util.LinkedHashMap<>();
        for (int size : FontSizeState.SIZES) {
            RadioMenuItem sizeItem = new RadioMenuItem(String.valueOf(size));
            sizeItem.setToggleGroup(fontSizeGroup);
            sizeItem.setSelected(size == initialFontSize);
            sizeItem.setOnAction(event -> onFontSizeSelected.accept(size));
            fontSizeItems.put(size, sizeItem);
        }
        Menu fontSizeMenu = new Menu("Font Size", null, fontSizeItems.values().toArray(MenuItem[]::new));
        Consumer<Integer> selectFontSize = size -> {
            RadioMenuItem item = fontSizeItems.get(size);
            if (item != null) {
                item.setSelected(true);
            }
        };

        CheckMenuItem searchBarToggle = barVisibilityToggle("Search bar");
        CheckMenuItem projectBarToggle = barVisibilityToggle("Project bar");
        CheckMenuItem relatedProjectBarToggle = barVisibilityToggle("Related-project bar");
        CheckMenuItem tagBarToggle = barVisibilityToggle("Tag bar");
        Menu viewMenu = new Menu("View", null,
                fontSizeMenu,
                new SeparatorMenuItem(),
                searchBarToggle,
                projectBarToggle,
                relatedProjectBarToggle,
                tagBarToggle);

        MenuItem inventoryItem = menuItem("Conversation Inventory", onShowInventory, errorHandler);
        MenuItem summarizeItem = menuItem("Summarize & tag", onSummarize, errorHandler);
        summarizeItem.setDisable(true);
        Menu toolsMenu = new Menu("Tools", null, inventoryItem, summarizeItem);

        Menu helpMenu = new Menu("Help");

        MenuBar menuBar = new MenuBar(fileMenu, viewMenu, toolsMenu, helpMenu);

        return new ToolbarWidgets(menuBar, exportChatItem, getLatestChatItem, inventoryItem, summarizeItem,
                selectFontSize, searchBarToggle, projectBarToggle, relatedProjectBarToggle, tagBarToggle);
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
        HBox searchBar = new HBox(CONTROL_GAP,
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
        HBox projectBar = new HBox(CONTROL_GAP,
                new Label("Project"),
                projectChoice,
                button("New", onNew, errorHandler),
                button("Assign", onAssign, errorHandler),
                button("Clear Project", onClear, errorHandler),
                button("Filter", onFilter, errorHandler));
        return new ProjectBarWidgets(projectBar, projectChoice);
    }

    /** The related-project bar: project choice plus add/remove/filter actions. */
    public static RelatedProjectBarWidgets createRelatedProjectBar(ThrowingRunnable onAdd,
            ThrowingRunnable onRemove, ThrowingRunnable onFilter, ThrowingRunnable onClearFilters,
            Consumer<Exception> errorHandler) {
        ComboBox<Project> relatedProjectChoice = new ComboBox<>();
        relatedProjectChoice.setPromptText("Related project");
        relatedProjectChoice.setConverter(namedProjectConverter());
        HBox relatedProjectBar = new HBox(CONTROL_GAP,
                new Label("Related Project"),
                relatedProjectChoice,
                button("Add", onAdd, errorHandler),
                button("Remove", onRemove, errorHandler),
                button("Filter", onFilter, errorHandler),
                button("Clear Filters", onClearFilters, errorHandler));
        return new RelatedProjectBarWidgets(relatedProjectBar, relatedProjectChoice);
    }

    /** The tag bar: tag choice plus new/add/remove/filter actions and a filters-wide clear. */
    public static TagBarWidgets createTagBar(ThrowingRunnable onNew, ThrowingRunnable onAdd,
            ThrowingRunnable onRemove, ThrowingRunnable onFilter, ThrowingRunnable onClearFilters,
            Consumer<Exception> errorHandler) {
        ComboBox<Tag> tagChoice = new ComboBox<>();
        tagChoice.setPromptText("Tag");
        tagChoice.setConverter(namedTagConverter());
        HBox tagBar = new HBox(CONTROL_GAP,
                new Label("Tag"),
                tagChoice,
                button("New", onNew, errorHandler),
                button("Add", onAdd, errorHandler),
                button("Remove", onRemove, errorHandler),
                button("Filter", onFilter, errorHandler),
                button("Clear Filters", onClearFilters, errorHandler));
        return new TagBarWidgets(tagBar, tagChoice);
    }

    /** Minimal prompt-routing pane backed by the application router service. */
    public static PromptPaneWidgets createPromptPane(ThrowingRunnable onSend, Consumer<Exception> errorHandler) {
        ComboBox<Project> projectChoice = new ComboBox<>();
        projectChoice.setPromptText("Prompt project");
        projectChoice.setConverter(namedProjectConverter());

        TextField conversationField = new TextField("current-task");
        conversationField.setPromptText("Conversation");
        conversationField.setMinWidth(180);

        ComboBox<Chat> resumeChatChoice = new ComboBox<>();
        resumeChatChoice.setPromptText("Resume chat");
        resumeChatChoice.setConverter(namedChatConverter());
        resumeChatChoice.setMinWidth(220);

        TextArea historyArea = createDetailTextArea();
        historyArea.setPromptText("History");
        historyArea.setPrefRowCount(6);

        TextArea promptArea = new TextArea();
        promptArea.setPromptText("Prompt");
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(4);
        promptArea.setMinWidth(160);

        Button sendButton = button("Send", onSend, errorHandler);
        Label classificationLabel = new Label("Classification: none");
        Label routeLabel = new Label("Route: none");
        classificationLabel.setWrapText(true);
        routeLabel.setWrapText(true);

        TextArea responseArea = createDetailTextArea();
        responseArea.setPromptText("Response");
        responseArea.setPrefRowCount(8);

        FlowPane controls = new FlowPane(CONTROL_GAP, SECTION_GAP,
                new Label("Prompt Project"),
                projectChoice,
                new Label("Conversation"),
                conversationField,
                new Label("Resume"),
                resumeChatChoice,
                sendButton);
        VBox resultLabels = new VBox(SECTION_GAP,
                classificationLabel,
                routeLabel);
        HBox promptColumns = new HBox(CONTROL_GAP,
                new VBox(SECTION_GAP, new Label("History"), historyArea),
                new VBox(SECTION_GAP, new Label("Prompt"), promptArea),
                new VBox(SECTION_GAP, new Label("Response"), responseArea));
        VBox pane = new VBox(SECTION_GAP,
                controls,
                resultLabels,
                promptColumns);
        pane.setPadding(new Insets(4));
        return new PromptPaneWidgets(pane, projectChoice, conversationField, resumeChatChoice, historyArea,
                promptArea, sendButton, classificationLabel, routeLabel, responseArea);
    }

    public static ListView<SearchResult> createChatListView(ChangeListener<SearchResult> selectionListener) {
        ListView<SearchResult> list = new ListView<>();
        list.setMinWidth(90);
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
        textArea.setMinWidth(140);
        return textArea;
    }

    public static BorderPane assembleRootPane(MenuBar menuBar, Node searchBar, Node projectBar,
            Node relatedProjectBar, Node tagBar, Node content, Node status) {
        BorderPane pane = new BorderPane();
        VBox top = new VBox(SECTION_GAP, menuBar, searchBar, projectBar, relatedProjectBar, tagBar);
        pane.setTop(top);
        pane.setCenter(content);
        pane.setBottom(new VBox(status));
        BorderPane.setMargin(searchBar, new Insets(4, 4, 0, 4));
        BorderPane.setMargin(projectBar, new Insets(0, 4, 0, 4));
        BorderPane.setMargin(relatedProjectBar, new Insets(0, 4, 0, 4));
        BorderPane.setMargin(tagBar, new Insets(0, 4, 0, 4));
        BorderPane.setMargin(content, new Insets(4));
        BorderPane.setMargin(status, new Insets(0, 4, 4, 4));
        return pane;
    }

    public static String formatResultRow(SearchResult result) {
        StringBuilder row = new StringBuilder();
        row.append(result.chat().title()).append("\n");
        row.append("Source: ").append(result.chat().source().displayName());
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

    public static StringConverter<Chat> namedChatConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Chat chat) {
                if (chat == null) {
                    return "";
                }
                String title = chat.title() == null || chat.title().isBlank() ? "Untitled chat" : chat.title();
                String session = chat.providerSessionId() == null || chat.providerSessionId().isBlank()
                        ? "" : " / " + chat.providerSessionId();
                return title + " [" + chat.id() + session + "]";
            }

            @Override
            public Chat fromString(String text) {
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
