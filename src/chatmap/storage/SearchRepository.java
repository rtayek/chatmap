package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import chatmap.domain.Chat;
import chatmap.domain.Source;

/** FTS-backed search queries over stored chats and messages. */
public final class SearchRepository {

    private final Connection conn;

    public SearchRepository(Connection conn) {
        this.conn = conn;
    }

    public List<Chat> searchChatsByMessageText(String ftsQuery) throws SQLException {
        if (ftsQuery == null || ftsQuery.isBlank()) {
            return List.of();
        }
        String sql = "SELECT DISTINCT c.id, c.projectId, c.source, c.title, c.createdAt, "
                + "c.updatedAt, c.importedAt, c.archived "
                + "FROM messageFts f "
                + "JOIN messages m ON m.id = f.rowid "
                + "JOIN chats c ON c.id = m.chatId "
                + "WHERE messageFts MATCH ? "
                + "ORDER BY c.importedAt, c.id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ftsQuery);
            try (ResultSet rs = ps.executeQuery()) {
                List<Chat> chats = new ArrayList<>();
                while (rs.next()) {
                    chats.add(readChat(rs));
                }
                return chats;
            }
        }
    }

    private static Chat readChat(ResultSet rs) throws SQLException {
        long projectId = rs.getLong("projectId");
        Long boxedProjectId = rs.wasNull() ? null : projectId;
        return new Chat(
                rs.getLong("id"),
                boxedProjectId,
                Source.fromDbValue(rs.getString("source")),
                rs.getString("title"),
                rs.getString("createdAt"),
                rs.getString("updatedAt"),
                rs.getString("importedAt"),
                rs.getInt("archived") != 0);
    }
}
