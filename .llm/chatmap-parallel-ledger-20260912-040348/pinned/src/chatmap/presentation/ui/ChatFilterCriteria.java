package chatmap.presentation.ui;

import chatmap.domain.SearchOptions;

/** Encapsulates immutable filter criteria (query, project ID, tag ID) for chat listing and search. */
public record ChatFilterCriteria(String query, Long projectId, Long tagId, Long relatedProjectId) {

    public static final ChatFilterCriteria EMPTY = new ChatFilterCriteria("", null, null, null);

    public ChatFilterCriteria {
        query = query == null ? "" : query.trim();
    }

    public boolean isEmpty() {
        return query.isEmpty() && projectId == null && tagId == null && relatedProjectId == null;
    }

    public ChatFilterCriteria withQuery(String newQuery) {
        return new ChatFilterCriteria(newQuery, projectId, tagId, relatedProjectId);
    }

    public ChatFilterCriteria withProjectId(Long newProjectId) {
        return new ChatFilterCriteria(query, newProjectId, tagId, relatedProjectId);
    }

    public ChatFilterCriteria withTagId(Long newTagId) {
        return new ChatFilterCriteria(query, projectId, newTagId, relatedProjectId);
    }

    public ChatFilterCriteria withRelatedProjectId(Long newRelatedProjectId) {
        return new ChatFilterCriteria(query, projectId, tagId, newRelatedProjectId);
    }

    public SearchOptions toSearchOptions() {
        return new SearchOptions(projectId, tagId, relatedProjectId, null);
    }
}
