package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chatmap.domain.Chat;
import chatmap.domain.SearchOptions;
import chatmap.domain.SearchResult;
import chatmap.domain.Source;
import chatmap.domain.Tag;

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
        return searchResultsByMessageText(ftsQuery, options).stream()
                .map(SearchResult::chat)
                .toList();
    }

    public List<SearchResult> listAllResults() throws SQLException {
        String sql = "SELECT c.id, c.projectId, c.source, c.title, c.createdAt, c.updatedAt, c.importedAt, "
                + "c.archived, p.name AS projectName "
                + "FROM chats c "
                + "LEFT JOIN projects p ON p.id = c.projectId "
                + "ORDER BY c.importedAt, c.id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<SearchResult> results = new ArrayList<>();
            while (rs.next()) {
                Chat chat = readChat(rs);
                results.add(new SearchResult(chat, rs.getString("projectName"), findTagsByChat(chat.id()), null));
            }
            return results;
        }
    }

    public List<SearchResult> searchResultsByMessageText(String ftsQuery) throws SQLException {
        return searchResultsByMessageText(ftsQuery, SearchOptions.none());
    }

    public List<SearchResult> searchResultsByMessageText(String ftsQuery, SearchOptions options) throws SQLException {
        if (ftsQuery == null || ftsQuery.isBlank()) {
            return List.of();
        }
        SearchOptions filters = options == null ? SearchOptions.none() : options;
        StringBuilder sql = new StringBuilder("SELECT c.id, c.projectId, c.source, c.title, c.createdAt, "
                + "c.updatedAt, c.importedAt, c.archived, p.name AS projectName, "
                + "snippet(messageFts, 0, '[', ']', '...', 12) AS snippet "
                + "FROM messageFts "
                + "JOIN messages m ON m.id = messageFts.rowid "
                + "JOIN chats c ON c.id = m.chatId ");
        sql.append("LEFT JOIN projects p ON p.id = c.projectId ");
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
        sql.append("ORDER BY c.importedAt, c.id, m.sequence");

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
                Map<Long, SearchResult> results = new LinkedHashMap<>();
                while (rs.next()) {
                    Chat chat = readChat(rs);
                    results.putIfAbsent(chat.id(), new SearchResult(
                            chat,
                            rs.getString("projectName"),
                            findTagsByChat(chat.id()),
                            rs.getString("snippet")));
                }
                return new ArrayList<>(results.values());
            }
        }
    }

    private List<Tag> findTagsByChat(long chatId) throws SQLException {
        String sql = "SELECT t.id, t.name FROM tags t "
                + "JOIN chatTags ct ON ct.tagId = t.id "
                + "WHERE ct.chatId = ? ORDER BY t.name COLLATE NOCASE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Tag> tags = new ArrayList<>();
                while (rs.next()) {
                    tags.add(new Tag(rs.getLong("id"), rs.getString("name")));
                }
                return tags;
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
