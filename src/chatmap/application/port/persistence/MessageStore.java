package chatmap.application.port.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import chatmap.domain.Message;

/** Application-facing persistence operations for messages. */
public interface MessageStore {

    void insertAll(List<Message> messages) throws SQLException;

    void deleteByChat(long chatId) throws SQLException;

    List<Message> findByChat(long chatId) throws SQLException;

    Map<Long, List<Message>> findByChatIds(List<Long> chatIds) throws SQLException;
}
