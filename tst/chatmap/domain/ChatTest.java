package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatTest {

    private static Chat sample() {
        return new Chat(7L, 3L, Source.chatgptJson, "Title",
                "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", "2026-01-03T00:00:00Z", false,
                new ImportMetadata("ext-1", "file:///a", "hash-1",
                        "2026-01-04T00:00:00Z", "2026-01-05T00:00:00Z"));
    }

    @Test
    void toBuilderWithNoChangesReturnsAnEqualChat() {
        Chat original = sample();
        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void builderChangesOnlyTheNamedField() {
        Chat original = sample();
        Chat changed = original.toBuilder().sourceUri("file:///b").build();

        assertEquals("file:///b", changed.sourceUri());
        // Everything else is untouched (guards against field transposition in build()).
        assertEquals(original.toBuilder().sourceUri(original.sourceUri()).build(), original);
        assertEquals(original.id(), changed.id());
        assertEquals(original.source(), changed.source());
        assertEquals(original.title(), changed.title());
        assertEquals(original.createdAt(), changed.createdAt());
        assertEquals(original.updatedAt(), changed.updatedAt());
        assertEquals(original.importedAt(), changed.importedAt());
        assertEquals(original.externalConversationId(), changed.externalConversationId());
        assertEquals(original.contentHash(), changed.contentHash());
        assertEquals(original.sourceUpdatedAt(), changed.sourceUpdatedAt());
        assertEquals(original.lastImportedAt(), changed.lastImportedAt());
        assertEquals(original.importMetadata().externalConversationId(),
                changed.importMetadata().externalConversationId());
        assertEquals(original.importMetadata().transcriptHash(),
                changed.importMetadata().transcriptHash());
    }

    @Test
    void builderChainsMultipleFieldChanges() {
        Chat changed = sample().toBuilder()
                .externalConversationId("ext-2")
                .transcriptHash("hash-2")
                .lastImportedAt("2026-02-01T00:00:00Z")
                .build();

        assertEquals("ext-2", changed.externalConversationId());
        assertEquals("hash-2", changed.contentHash());
        assertEquals("hash-2", changed.importMetadata().transcriptHash());
        assertEquals("2026-02-01T00:00:00Z", changed.lastImportedAt());
        assertEquals("file:///a", changed.sourceUri());
    }
}
