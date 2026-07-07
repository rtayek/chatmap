package chatmap.domain;

/** Optional filters for chat search. */
public record SearchOptions(
        Long projectId,
        Long tagId,
        Boolean archived) {

    public static SearchOptions none() {
        return new SearchOptions(null, null, null);
    }
}
