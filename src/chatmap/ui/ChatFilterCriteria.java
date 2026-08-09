package chatmap.ui;

import chatmap.domain.SearchOptions;

/** Encapsulates immutable filter criteria (query, project ID, tag ID) for chat listing and search. */
public record ChatFilterCriteria(String query, Long projectId, Long tagId) {

    public static final ChatFilterCriteria EMPTY = new ChatFilterCriteria("", null, null);

    public ChatFilterCriteria {
        query = query == null ? "" : query.trim();
    }

    public boolean isEmpty() {
        return query.isEmpty() && projectId == null && tagId == null;
    }

    public ChatFilterCriteria withQuery(String newQuery) {
        return new ChatFilterCriteria(newQuery, projectId, tagId);
    }

    public ChatFilterCriteria withProjectId(Long newProjectId) {
        return new ChatFilterCriteria(query, newProjectId, tagId);
    }

    public ChatFilterCriteria withTagId(Long newTagId) {
        return new ChatFilterCriteria(query, projectId, newTagId);
    }

    public SearchOptions toSearchOptions() {
        return new SearchOptions(projectId, tagId, null);
    }
}
