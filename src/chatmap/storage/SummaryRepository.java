package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import chatmap.domain.ChatSummary;

/**
 * CRUD for chat summaries. Holds a Connection supplied by the caller; does
 * not own it. Summaries are purely additive: nothing here ever writes to
 * chats or messages.
 */
public final class SummaryRepository {

    private final Connection conn;

    public SummaryRepository(Connection conn) {
        this.conn = conn;
    }

    /** Inserts a summary; the id field of the argument is ignored. Returns the stored summary with its new id. */
    public ChatSummary insert(ChatSummary summary) throws SQLException {
        String sql = "INSERT INTO chatSummaries (chatId, summary, generatedBy, generatedAt) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, summary.chatId());
            ps.setString(2, summary.summary());
            ps.setString(3, summary.generatedBy());
            ps.setString(4, summary.generatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return new ChatSummary(id, summary.chatId(), summary.summary(),
                        summary.generatedBy(), summary.generatedAt());
            }
        }
    }

    /** Most recent summary for a chat, if any. */
    public Optional<ChatSummary> findLatestForChat(long chatId) throws SQLException {
        String sql = "SELECT id, chatId, summary, generatedBy, generatedAt FROM chatSummaries "
                + "WHERE chatId = ? ORDER BY generatedAt DESC, id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(rs));
            }
        }
    }

    /** All summaries for a chat, oldest first. */
    public List<ChatSummary> findAllForChat(long chatId) throws SQLException {
        String sql = "SELECT id, chatId, summary, generatedBy, generatedAt FROM chatSummaries "
                + "WHERE chatId = ? ORDER BY generatedAt, id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ChatSummary> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(read(rs));
                }
                return out;
            }
        }
    }

    public void delete(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chatSummaries WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static ChatSummary read(ResultSet rs) throws SQLException {
        return new ChatSummary(
                rs.getLong("id"),
                rs.getLong("chatId"),
                rs.getString("summary"),
                rs.getString("generatedBy"),
                rs.getString("generatedAt"));
    }
}
