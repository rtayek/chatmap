package chatmap.application.port.persistence;

import java.sql.SQLException;
import java.util.Optional;

import chatmap.domain.ChatSummary;

/** Application-facing persistence operations for generated summaries. */
public interface SummaryStore {

    ChatSummary insert(ChatSummary summary) throws SQLException;

    Optional<ChatSummary> findLatestForChat(long chatId) throws SQLException;
}
