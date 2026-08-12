package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SearchOptionsTest {

    @Test
    void noneIsAnAllNullSentinel() {
        SearchOptions options = SearchOptions.none();

        assertNull(options.projectId());
        assertNull(options.tagId());
        assertNull(options.archived());
    }
}
