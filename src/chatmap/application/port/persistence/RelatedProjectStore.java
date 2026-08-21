package chatmap.application.port.persistence;

import java.sql.SQLException;
import java.util.List;

import chatmap.domain.Chat;
import chatmap.domain.Project;

/** Application-facing persistence operations for chat related-project links. */
public interface RelatedProjectStore {

    void assignToChat(long chatId, long projectId) throws SQLException;

    void removeFromChat(long chatId, long projectId) throws SQLException;

    List<Project> findByChat(long chatId) throws SQLException;

    List<Chat> findChatsByProject(long projectId) throws SQLException;
}
