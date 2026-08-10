package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chatmap.domain.Message;

/**
 * CRUD for messages plus FTS-backed text search.
 * Holds a Connection supplied by the caller; does not own it.
 *
 * The messageFts index is maintained by triggers in schema.sql; this class
 * never writes to messageFts directly.
 */
public final class MessageRepository {

    private final Connection conn;

    public MessageRepository(Connection conn) {
        this.conn = conn;
    }

    /** Inserts a message; the id field of the argument is ignored. Returns the stored message with its new id. */
    public Message insert(Message m) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT INTO messages (chatId, role, text, sequence, timestamp, rawJson) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, m.chatId());
                ps.setString(2, m.role());
                ps.setString(3, m.text());
                ps.setInt(4, m.sequence());
                ps.setString(5, m.timestamp());
                ps.setString(6, m.rawJson());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    long id = keys.getLong(1);
                    return new Message(id, m.chatId(), m.role(), m.text(), m.sequence(), m.timestamp(), m.rawJson());
                }
            }
        }
    }

    /** Inserts a list of messages in a single JDBC batch operation. */
    public void insertAll(List<Message> messageList) throws SQLException {
        synchronized (conn) {
            if (messageList.isEmpty()) {
                return;
            }
            String sql = "INSERT INTO messages (chatId, role, text, sequence, timestamp, rawJson) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Message m : messageList) {
                    ps.setLong(1, m.chatId());
                    ps.setString(2, m.role());
                    ps.setString(3, m.text());
                    ps.setInt(4, m.sequence());
                    ps.setString(5, m.timestamp());
                    ps.setString(6, m.rawJson());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    public void updateText(long id, String newText) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE messages SET text = ? WHERE id = ?")) {
                ps.setString(1, newText);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
        }
    }

    public void delete(long id) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM messages WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        }
    }

    public void deleteByChat(long chatId) throws SQLException {
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM messages WHERE chatId = ?")) {
                ps.setLong(1, chatId);
                ps.executeUpdate();
            }
        }
    }

    /** Messages of a chat in sequence order. */
    public List<Message> findByChat(long chatId) throws SQLException {
        synchronized (conn) {
            String sql = "SELECT id, chatId, role, text, sequence, timestamp, rawJson "
                    + "FROM messages WHERE chatId = ? ORDER BY sequence";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, chatId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Message> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(read(rs));
                    }
                    return out;
                }
            }
        }
    }

    /** Messages for each requested chat, ordered by message sequence within each chat. */
    public Map<Long, List<Message>> findByChatIds(List<Long> chatIds) throws SQLException {
        synchronized (conn) {
            Map<Long, List<Message>> byChat = emptyListsByChatId(chatIds);
            if (byChat.isEmpty()) {
                return byChat;
            }
            String sql = "SELECT id, chatId, role, text, sequence, timestamp, rawJson "
                    + "FROM messages WHERE chatId IN (" + placeholders(byChat.size()) + ") "
                    + "ORDER BY chatId, sequence";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = 1;
                for (Long chatId : byChat.keySet()) {
                    ps.setLong(index++, chatId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Message message = read(rs);
                        byChat.get(message.chatId()).add(message);
                    }
                    return byChat;
                }
            }
        }
    }

    /**
     * Full-text search over message text via FTS5. Returns matching message ids.
     * The query uses FTS5 MATCH syntax; a bare word or phrase is fine for MVP.
     * Richer, filtered search belongs to SearchRepository (build order step 9).
     */
    public List<Long> searchText(String ftsQuery) throws SQLException {
        synchronized (conn) {
            String sql = "SELECT rowid FROM messageFts WHERE messageFts MATCH ? ORDER BY rank";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ftsQuery);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Long> ids = new ArrayList<>();
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                    }
                    return ids;
                }
            }
        }
    }

    private static Message read(ResultSet rs) throws SQLException {
        return new Message(
                rs.getLong("id"),
                rs.getLong("chatId"),
                rs.getString("role"),
                rs.getString("text"),
                rs.getInt("sequence"),
                rs.getString("timestamp"),
                rs.getString("rawJson"));
    }

    private static Map<Long, List<Message>> emptyListsByChatId(List<Long> chatIds) {
        Map<Long, List<Message>> byChat = new LinkedHashMap<>();
        for (Long chatId : chatIds) {
            byChat.putIfAbsent(chatId, new ArrayList<>());
        }
        return byChat;
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }
}
