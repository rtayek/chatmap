package chatmap.application.port.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import chatmap.domain.Tag;

/** Application-facing persistence operations for tags. */
public interface TagStore {

    Tag insert(Tag tag) throws SQLException;

    Optional<Tag> findById(long id) throws SQLException;

    Optional<Tag> findByName(String name) throws SQLException;

    List<Tag> findAll() throws SQLException;

    void update(Tag tag) throws SQLException;

    void delete(long id) throws SQLException;

    void assignToChat(long chatId, long tagId) throws SQLException;

    void removeFromChat(long chatId, long tagId) throws SQLException;

    List<Tag> findByChat(long chatId) throws SQLException;

    Map<Long, List<Tag>> findByChatIds(List<Long> chatIds) throws SQLException;
}
