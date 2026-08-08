package chatmap.ui;

import java.util.List;

import chatmap.domain.Project;
import chatmap.domain.SearchResult;
import chatmap.domain.Tag;
import javafx.util.StringConverter;

/** Helper methods for formatting search results, file names, and combo box StringConverters. */
public final class ChatMapViewBuilder {

    private ChatMapViewBuilder() {
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
