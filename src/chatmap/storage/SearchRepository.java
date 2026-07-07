package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import chatmap.domain.Chat;
import chatmap.domain.SearchOptions;
import chatmap.domain.Source;

/** FTS-backed search queries over stored chats and messages. */
public final class SearchRepository {

    private final Connection conn;

    public SearchRepository(Connection conn) {
        this.conn = conn;
    }

    public List<Chat> searchChatsByMessageText(String ftsQuery) throws SQLException {
        return searchChatsByMessageText(ftsQuery, SearchOptions.none());
    }

    public List<Chat> searchChatsByMessageText(String ftsQuery, SearchOptions options) throws SQLException {
        if (ftsQuery == null || ftsQuery.isBlank()) {
            return List.of();
        }
        SearchOptions filters = options == null ? SearchOptions.none() : options;
        StringBuilder sql = new StringBuilder("SELECT DISTINCT c.id, c.projectId, c.source, c.title, c.createdAt, "
                + "c.updatedAt, c.importedAt, c.archived "
                + "FROM messageFts f "
                + "JOIN messages m ON m.id = f.rowid "
                + "JOIN chats c ON c.id = m.chatId ");
        if (filters.tagId() != null) {
            sql.append("JOIN chatTags ct ON ct.chatId = c.id ");
        }
        sql.append("WHERE messageFts MATCH ? ");
        if (filters.projectId() != null) {
            sql.append("AND c.projectId = ? ");
        }
        if (filters.tagId() != null) {
            sql.append("AND ct.tagId = ? ");
        }
        if (filters.archived() != null) {
            sql.append("AND c.archived = ? ");
        }
        sql.append("ORDER BY c.importedAt, c.id");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int parameter = 1;
            ps.setString(parameter++, ftsQuery);
            if (filters.projectId() != null) {
                ps.setLong(parameter++, filters.projectId());
            }
            if (filters.tagId() != null) {
                ps.setLong(parameter++, filters.tagId());
            }
            if (filters.archived() != null) {
                ps.setInt(parameter++, filters.archived() ? 1 : 0);
            }
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
