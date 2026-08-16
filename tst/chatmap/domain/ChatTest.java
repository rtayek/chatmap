package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChatTest {

    private static Chat sample() {
        return Chat.builder()
                .id(7L)
                .projectId(3L)
                .source(Source.chatgptJson)
                .title("Title")
                .createdAt("2026-01-01T00:00:00Z")
                .updatedAt("2026-01-02T00:00:00Z")
                .importedAt("2026-01-03T00:00:00Z")
                .archived(false)
                .externalConversationId("ext-1")
                .sourceUri("file:///a")
                .contentHash("hash-1")
                .sourceUpdatedAt("2026-01-04T00:00:00Z")
                .lastImportedAt("2026-01-05T00:00:00Z")
                .build();
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
                .contentHash("hash-2")
                .lastImportedAt("2026-02-01T00:00:00Z")
                .build();

        assertEquals("ext-2", changed.externalConversationId());
        assertEquals("hash-2", changed.contentHash());
        assertEquals("hash-2", changed.importMetadata().transcriptHash());
        assertEquals("2026-02-01T00:00:00Z", changed.lastImportedAt());
        assertEquals("file:///a", changed.sourceUri());
    }

    @Test
    void eightArgConstructorDefaultsOriginatedByToImportedAndImportMetadataToNone() {
        Chat chat = Chat.builder()
                            .id(1L)
                            .projectId(null)
                            .source(Source.plainText)
                            .title("Title")
                            .createdAt("2026-01-01T00:00:00Z")
                            .updatedAt("2026-01-02T00:00:00Z")
                            .importedAt("2026-01-03T00:00:00Z")
                            .archived(false)
                            .build();

        assertEquals(ChatOrigin.imported, chat.originatedBy());
        assertNull(chat.externalConversationId());
        assertNull(chat.sourceUri());
        assertNull(chat.contentHash());
        // ImportMetadata.none() carries updatedAt/importedAt through as sourceUpdatedAt/lastImportedAt,
        // not left null or transposed with each other.
        assertEquals("2026-01-02T00:00:00Z", chat.sourceUpdatedAt());
        assertEquals("2026-01-03T00:00:00Z", chat.lastImportedAt());
    }

    @Test
    void nineArgConstructorUsesGivenOriginatedByAndStillDefaultsImportMetadataToNone() {
        Chat chat = Chat.builder()
                            .id(1L)
                            .projectId(null)
                            .source(Source.jshellHarness)
                            .title("Title")
                            .createdAt("2026-01-01T00:00:00Z")
                            .updatedAt("2026-01-02T00:00:00Z")
                            .importedAt("2026-01-03T00:00:00Z")
                            .archived(false)
                            .originatedBy(ChatOrigin.generated)
                            .build();

        assertEquals(ChatOrigin.generated, chat.originatedBy());
        assertNull(chat.externalConversationId());
        assertEquals("2026-01-02T00:00:00Z", chat.sourceUpdatedAt());
        assertEquals("2026-01-03T00:00:00Z", chat.lastImportedAt());
    }

    @Test
    void nineArgImportMetadataConstructorDefaultsOriginatedByToImported() {
        ImportMetadata metadata = new ImportMetadata("ext-1", "file:///a", "hash-1",
                "2026-01-04T00:00:00Z", "2026-01-05T00:00:00Z");
        Chat chat = Chat.builder()
                .id(1L)
                .source(Source.chatgptJson)
                .title("Title")
                .createdAt("2026-01-01T00:00:00Z")
                .updatedAt("2026-01-02T00:00:00Z")
                .importedAt("2026-01-03T00:00:00Z")
                .externalConversationId(metadata.externalConversationId())
                .sourceUri(metadata.sourceUri())
                .contentHash(metadata.transcriptHash())
                .sourceUpdatedAt(metadata.sourceUpdatedAt())
                .lastImportedAt(metadata.lastImportedAt())
                .build();

        assertEquals(ChatOrigin.imported, chat.originatedBy());
        assertEquals(metadata, chat.importMetadata());
    }

    @Test
    void rejectsNullClosedSetAndMetadataFields() {
        ImportMetadata metadata = ImportMetadata.none(null, "2026-01-03T00:00:00Z");

        assertThrows(NullPointerException.class, () -> new Chat(1L, null, null, "Title", null, null, "2026-01-03T00:00:00Z", false, metadata, ChatOrigin.imported));
        assertThrows(NullPointerException.class, () -> new Chat(1L, null, Source.plainText, "Title", null, null, "2026-01-03T00:00:00Z", false, null, ChatOrigin.imported));
        assertThrows(NullPointerException.class, () -> new Chat(1L, null, Source.plainText, "Title", null, null, "2026-01-03T00:00:00Z", false, metadata, null));
    }
}
