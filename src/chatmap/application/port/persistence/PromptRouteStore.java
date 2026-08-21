package chatmap.application.port.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import chatmap.domain.PromptRouteRecord;

/** Application-facing persistence operations for routed prompt metadata. */
public interface PromptRouteStore {
    PromptRouteRecord insert(PromptRouteRecord record) throws SQLException;

    Optional<PromptRouteRecord> findByChatId(long chatId) throws SQLException;

    List<PromptRouteRecord> findByWorkingProjectAndConversation(String workingProjectIdentity, String conversationId)
            throws SQLException;
}
