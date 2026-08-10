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
import chatmap.domain.Tag;

/** FTS-backed search queries over stored chats and messages. */
public final class SearchRepository {

    private final Connection conn;

    public SearchRepository(Connection conn) {
        this.conn = conn;
    }

    public List<Chat> searchChatsByMessageText(String ftsQuery) throws SQLException {
        synchronized (conn) {
            return searchChatsByMessageText(ftsQuery, SearchOptions.none());
        }
    }

    public List<Chat> searchChatsByMessageText(String ftsQuery, SearchOptions options) throws SQLException {
        synchronized (conn) {
            return searchResultsByMessageText(ftsQuery, options).stream()
                    .map(SearchResult::chat)
                    .toList();
        }
    }

    public List<SearchResult> listAllResults() throws SQLException {
        synchronized (conn) {
            return listResults(SearchOptions.none());
        }
    }

    public List<SearchResult> listResults(SearchOptions options) throws SQLException {
        synchronized (conn) {
            SearchOptions filters = options == null ? SearchOptions.none() : options;
            StringBuilder sql = new StringBuilder(
                    ChatRowMapper.SELECT_C_COLUMNS
                    + ", p.name AS projectName "
                    + "FROM chats c "
                    + "LEFT JOIN projects p ON p.id = c.projectId ");
            if (filters.tagId() != null) {
                sql.append("JOIN chatTags ct ON ct.chatId = c.id ");
            }
            sql.append("WHERE 1 = 1 ");
            appendFilterConditions(sql, filters);
            sql.append("ORDER BY c.importedAt, c.id");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bindFilterParameters(ps, 1, filters);
                try (ResultSet rs = ps.executeQuery()) {
                    List<ResultRow> rows = new ArrayList<>();
                    while (rs.next()) {
                        Chat chat = ChatRowMapper.read(rs);
                        rows.add(new ResultRow(chat, rs.getString("projectName"), null));
                    }
                    return withTags(rows);
                }
            }
        }
    }

    public List<SearchResult> searchResultsByMessageText(String ftsQuery) throws SQLException {
        synchronized (conn) {
            return searchResultsByMessageText(ftsQuery, SearchOptions.none());
        }
    }

    public List<SearchResult> searchResultsByMessageText(String ftsQuery, SearchOptions options) throws SQLException {
        synchronized (conn) {
            if (ftsQuery == null || ftsQuery.isBlank()) {
                return List.of();
            }
            SearchOptions filters = options == null ? SearchOptions.none() : options;
            StringBuilder sql = new StringBuilder(ChatRowMapper.SELECT_C_COLUMNS
                    + ", p.name AS projectName, "
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
            appendFilterConditions(sql, filters);
            sql.append("ORDER BY relevance, c.importedAt, c.id, m.sequence, m.id");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setString(1, ftsQuery);
                bindFilterParameters(ps, 2, filters);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<Long, ResultRow> rows = new LinkedHashMap<>();
                    while (rs.next()) {
                        Chat chat = ChatRowMapper.read(rs);
                        rows.putIfAbsent(chat.id(), new ResultRow(
                                chat,
                                rs.getString("projectName"),
                                rs.getString("snippet")));
                    }
                    return withTags(new ArrayList<>(rows.values()));
                }
            }
        }
    }

    private List<SearchResult> withTags(List<ResultRow> rows) throws SQLException {
        synchronized (conn) {
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
    }

    private static final int BATCH_SIZE = 500;

    private Map<Long, List<Tag>> findTagsByChatIds(List<Long> chatIds) throws SQLException {
        synchronized (conn) {
            if (chatIds.isEmpty()) {
                return Map.of();
            }

            Map<Long, List<Tag>> tagsByChat = new LinkedHashMap<>();
            for (int i = 0; i < chatIds.size(); i += BATCH_SIZE) {
                List<Long> batch = chatIds.subList(i, Math.min(chatIds.size(), i + BATCH_SIZE));
                fetchTagsBatch(batch, tagsByChat);
            }
            return tagsByChat;
        }
    }

    private void fetchTagsBatch(List<Long> batch, Map<Long, List<Tag>> tagsByChat) throws SQLException {
        synchronized (conn) {
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            String sql = "SELECT ct.chatId, t.id, t.name FROM tags t "
                    + "JOIN chatTags ct ON ct.tagId = t.id "
                    + "WHERE ct.chatId IN (" + placeholders + ") "
                    + "ORDER BY ct.chatId, t.name COLLATE NOCASE, t.id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < batch.size(); i++) {
                    ps.setLong(i + 1, batch.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long chatId = rs.getLong("chatId");
                        tagsByChat.computeIfAbsent(chatId, ignored -> new ArrayList<>())
                                .add(new Tag(rs.getLong("id"), rs.getString("name")));
                    }
                }
            }
        }
    }

    private record ResultRow(Chat chat, String projectName, String snippet) {
    }

    private static void appendFilterConditions(StringBuilder sql, SearchOptions filters) {
        if (filters.projectId() != null) {
            sql.append("AND c.projectId = ? ");
        }
        if (filters.tagId() != null) {
            sql.append("AND ct.tagId = ? ");
        }
        if (filters.archived() != null) {
            sql.append("AND c.archived = ? ");
        }
    }

    private static int bindFilterParameters(PreparedStatement ps, int startParameterIndex, SearchOptions filters)
            throws SQLException {
        int parameter = startParameterIndex;
        if (filters.projectId() != null) {
            ps.setLong(parameter++, filters.projectId());
        }
        if (filters.tagId() != null) {
            ps.setLong(parameter++, filters.tagId());
        }
        if (filters.archived() != null) {
            ps.setInt(parameter++, filters.archived() ? 1 : 0);
        }
        return parameter;
    }
}
