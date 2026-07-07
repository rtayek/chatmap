package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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

    public void updateText(long id, String newText) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE messages SET text = ? WHERE id = ?")) {
            ps.setString(1, newText);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void delete(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM messages WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /** Messages of a chat in sequence order. */
    public List<Message> findByChat(long chatId) throws SQLException {
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

    /**
     * Full-text search over message text via FTS5. Returns matching message ids.
     * The query uses FTS5 MATCH syntax; a bare word or phrase is fine for MVP.
     * Richer, filtered search belongs to SearchRepository (build order step 9).
     */
    public List<Long> searchText(String ftsQuery) throws SQLException {
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
}
