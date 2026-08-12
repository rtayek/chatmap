package chatmap.backend.web;

/**
 * Outcome of an exhaustive web conversation discovery attempt.
 *
 * {@code complete} means the implementation reached a verified terminal
 * condition (an authenticated API said there is no more, or the scroll
 * position genuinely stopped advancing across repeated waits) — not merely
 * "no newly discovered chats this round." Everything else is honest about
 * not having proven the end of the list.
 */
enum DiscoveryStatus {
    /** Reached a verified terminal condition; the returned list is the complete scope. */
    complete,
    /** Enumeration stopped (iteration budget, selector miss) without proving the end. */
    incomplete,
    /** Browser, authentication, CDP, or the provider's UI was unavailable. */
    unavailable,
    /** An unexpected error prevented enumeration. */
    failed
}
