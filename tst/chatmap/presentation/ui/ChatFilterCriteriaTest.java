package chatmap.presentation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import chatmap.domain.SearchOptions;

class ChatFilterCriteriaTest {

    @Test
    void emptyCriteriaDefaults() {
        ChatFilterCriteria criteria = ChatFilterCriteria.EMPTY;

        assertEquals("", criteria.query());
        assertNull(criteria.projectId());
        assertNull(criteria.tagId());
        assertNull(criteria.relatedProjectId());
        assertTrue(criteria.isEmpty());
    }

    @Test
    void nullQueryTrimsToEmpty() {
        ChatFilterCriteria criteria = new ChatFilterCriteria(null, 1L, 2L, 3L);

        assertEquals("", criteria.query());
        assertEquals(1L, criteria.projectId());
        assertEquals(2L, criteria.tagId());
        assertEquals(3L, criteria.relatedProjectId());
        assertFalse(criteria.isEmpty());
    }

    @Test
    void immutablyUpdatesQueryProjectAndTag() {
        ChatFilterCriteria initial = ChatFilterCriteria.EMPTY;

        ChatFilterCriteria withQuery = initial.withQuery(" test query ");
        assertEquals("test query", withQuery.query());
        assertFalse(withQuery.isEmpty());

        ChatFilterCriteria withProject = withQuery.withProjectId(10L);
        assertEquals(10L, withProject.projectId());
        assertEquals("test query", withProject.query());

        ChatFilterCriteria withTag = withProject.withTagId(20L);
        assertEquals(20L, withTag.tagId());
        assertEquals(10L, withTag.projectId());
        assertEquals("test query", withTag.query());

        ChatFilterCriteria withRelatedProject = withTag.withRelatedProjectId(30L);
        assertEquals(30L, withRelatedProject.relatedProjectId());
        assertEquals(20L, withRelatedProject.tagId());
        assertEquals(10L, withRelatedProject.projectId());
        assertEquals("test query", withRelatedProject.query());

        SearchOptions options = withRelatedProject.toSearchOptions();
        assertEquals(10L, options.projectId());
        assertEquals(20L, options.tagId());
        assertEquals(30L, options.relatedProjectId());
    }
}
