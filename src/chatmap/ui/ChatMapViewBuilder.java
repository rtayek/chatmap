package chatmap.ui;

import java.util.List;

import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Helper methods for constructing JavaFX nodes, formatting search results, and converters. */
public final class ChatMapViewBuilder {

    public static final String COMPACT_BUTTON_STYLE = "-fx-padding: 3 7 3 7;";

    private ChatMapViewBuilder() {
    }

    public static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setStyle(COMPACT_BUTTON_STYLE);
        button.setOnAction(event -> {
            event.consume();
            action.run();
        });
        return button;
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
