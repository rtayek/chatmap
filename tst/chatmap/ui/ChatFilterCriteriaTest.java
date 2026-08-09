package chatmap.ui;

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
        assertTrue(criteria.isEmpty());
    }

    @Test
    void nullQueryTrimsToEmpty() {
        ChatFilterCriteria criteria = new ChatFilterCriteria(null, 1L, 2L);

        assertEquals("", criteria.query());
        assertEquals(1L, criteria.projectId());
        assertEquals(2L, criteria.tagId());
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

        SearchOptions options = withTag.toSearchOptions();
        assertEquals(10L, options.projectId());
        assertEquals(20L, options.tagId());
    }
}
