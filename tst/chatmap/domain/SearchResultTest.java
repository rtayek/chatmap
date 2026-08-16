package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SearchResultTest {

    private static Chat sampleChat(long id) {
        return Chat.builder()
                       .id(id)
                       .projectId(null)
                       .source(Source.plainText)
                       .title("Title")
                       .createdAt("2026-01-01T00:00:00Z")
                       .updatedAt("2026-01-02T00:00:00Z")
                       .importedAt("2026-01-03T00:00:00Z")
                       .archived(false)
                       .build();
    }

    @Test
    void nullTagsBecomesEmptyListRatherThanNull() {
        SearchResult result = new SearchResult(sampleChat(1L), null, null, "snippet");

        assertTrue(result.tags().isEmpty());
    }

    @Test
    void tagsListIsDefensivelyCopiedSoCallerMutationDoesNotLeakIn() {
        List<Tag> mutable = new ArrayList<>(List.of(new Tag(1L, "mvp")));
        SearchResult result = new SearchResult(sampleChat(1L), null, mutable, "snippet");

        mutable.add(new Tag(2L, "later"));

        assertEquals(1, result.tags().size());
    }

    @Test
    void tagsListReturnedIsUnmodifiable() {
        SearchResult result = new SearchResult(sampleChat(1L), null, List.of(new Tag(1L, "mvp")), "snippet");

        assertThrows(UnsupportedOperationException.class, () -> result.tags().add(new Tag(2L, "later")));
    }

    @Test
    void chatIdDelegatesToTheWrappedChatsId() {
        SearchResult result = new SearchResult(sampleChat(42L), null, null, null);

        assertEquals(42L, result.chatId());
    }
}
