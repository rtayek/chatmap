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
        return listResults(SearchOptions.none());
    }

    public List<SearchResult> listResults(SearchOptions options) throws SQLException {
        SearchOptions filters = options == null ? SearchOptions.none() : options;
        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.projectId, c.source, c.title, c.createdAt, c.updatedAt, c.importedAt, "
                + "c.archived, c.externalConversationId, c.sourceUri, c.contentHash, c.sourceUpdatedAt, "
                + "c.lastImportedAt, p.name AS projectName "
                + "FROM chats c "
                + "LEFT JOIN projects p ON p.id = c.projectId ");
        if (filters.tagId() != null) {
            sql.append("JOIN chatTags ct ON ct.chatId = c.id ");
        }
        sql.append("WHERE 1 = 1 ");
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
            if (filters.projectId() != null) {
                ps.setLong(parameter++, filters.projectId());
            }
            if (filters.tagId() != null) {
                ps.setLong(parameter++, filters.tagId());
            }
            if (filters.archived() != null) {
                ps.setInt(parameter, filters.archived() ? 1 : 0);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ResultRow> rows = new ArrayList<>();
                while (rs.next()) {
                    Chat chat = readChat(rs);
                    rows.add(new ResultRow(chat, rs.getString("projectName"), null));
                }
                return withTags(rows);
            }
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
                + "c.updatedAt, c.importedAt, c.archived, c.externalConversationId, c.sourceUri, "
                + "c.contentHash, c.sourceUpdatedAt, c.lastImportedAt, p.name AS projectName, "
                + "snippet(messageFts, 0, '[', ']', '...', 12) AS snippet, "
                + "bm25(messageFts) AS relevance "
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
        sql.append("ORDER BY relevance, c.importedAt, c.id, m.sequence, m.id");

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
                Map<Long, ResultRow> rows = new LinkedHashMap<>();
                while (rs.next()) {
                    Chat chat = readChat(rs);
                    rows.putIfAbsent(chat.id(), new ResultRow(
                            chat,
                            rs.getString("projectName"),
                            rs.getString("snippet")));
                }
                return withTags(new ArrayList<>(rows.values()));
            }
        }
    }

    private List<SearchResult> withTags(List<ResultRow> rows) throws SQLException {
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Tag>> tagsByChat = findTagsByChatIds(rows.stream()
                .map(row -> row.chat().id())
                .toList());
        List<SearchResult> results = new ArrayList<>();
        for (ResultRow row : rows) {
            results.add(new SearchResult(
                    row.chat(),
                    row.projectName(),
                    tagsByChat.getOrDefault(row.chat().id(), List.of()),
                    row.snippet()));
        }
        return results;
    }

    private Map<Long, List<Tag>> findTagsByChatIds(List<Long> chatIds) throws SQLException {
        if (chatIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(chatIds.size(), "?"));
        String sql = "SELECT ct.chatId, t.id, t.name FROM tags t "
                + "JOIN chatTags ct ON ct.tagId = t.id "
                + "WHERE ct.chatId IN (" + placeholders + ") "
                + "ORDER BY ct.chatId, t.name COLLATE NOCASE, t.id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < chatIds.size(); i++) {
                ps.setLong(i + 1, chatIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                Map<Long, List<Tag>> tagsByChat = new LinkedHashMap<>();
                while (rs.next()) {
                    long chatId = rs.getLong("chatId");
                    tagsByChat.computeIfAbsent(chatId, ignored -> new ArrayList<>())
                            .add(new Tag(rs.getLong("id"), rs.getString("name")));
                }
                return tagsByChat;
            }
        }
    }

    private record ResultRow(Chat chat, String projectName, String snippet) {
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
                rs.getInt("archived") != 0,
                rs.getString("externalConversationId"),
                rs.getString("sourceUri"),
                rs.getString("contentHash"),
                rs.getString("sourceUpdatedAt"),
                rs.getString("lastImportedAt"));
    }
}
