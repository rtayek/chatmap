package chatmap.domain;

/**
 * One message within a chat.
 *
 * text     - normalized plain-text form used for display, search, and export.
 * rawJson  - optional original source payload for future reprocessing;
 *            null for plain text / Markdown imports.
 * timestamp- UTC ISO-8601 string when the source provides one, else null
 *            (chat-level import metadata is the fallback).
 */
public record Message(
        long id,
        long chatId,
        String role,
        String text,
        int sequence,
        String timestamp,
        String rawJson) {
}
