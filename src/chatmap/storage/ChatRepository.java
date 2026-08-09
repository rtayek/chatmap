package chatmap.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.Source;

/**
 * CRUD for chats. Holds a Connection supplied by the caller; does not own it.
 * (Single shared connection also keeps :memory: test databases coherent.)
 */
public final class ChatRepository {

    private final Connection conn;

    public ChatRepository(Connection conn) {
        this.conn = conn;
    }

    public Connection connection() {
        return conn;
    }

    /** Inserts a chat; the id field of the argument is ignored. Returns the stored chat with its new id. */
    public Chat insert(Chat chat) throws SQLException {
        String sql = "INSERT INTO chats (projectId, source, title, createdAt, updatedAt, importedAt, archived, "
                + "externalConversationId, sourceUri, contentHash, sourceUpdatedAt, lastImportedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setNullableLong(ps, 1, chat.projectId());
            ps.setString(2, chat.source().dbValue());
            ps.setString(3, chat.title());
            ps.setString(4, chat.createdAt());
            ps.setString(5, chat.updatedAt());
            ps.setString(6, chat.importedAt());
            ps.setInt(7, chat.archived() ? 1 : 0);
            ps.setString(8, chat.externalConversationId());
            ps.setString(9, chat.sourceUri());
            ps.setString(10, chat.contentHash());
            ps.setString(11, chat.sourceUpdatedAt());
            ps.setString(12, chat.lastImportedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return chat.toBuilder().id(id).build();
            }
        }
    }

    public Optional<Chat> findById(long id) throws SQLException {
        String sql = ChatRowMapper.selectColumns()
                + "FROM chats WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(ChatRowMapper.read(rs));
            }
        }
    }

    public List<Chat> findAll() throws SQLException {
        String sql = ChatRowMapper.selectColumns()
                + "FROM chats ORDER BY importedAt, id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return readAll(rs);
        }
    }

    public Optional<Chat> findMostRecent() throws SQLException {
        String sql = ChatRowMapper.selectColumns()
                + "FROM chats ORDER BY importedAt DESC, id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(ChatRowMapper.read(rs));
        }
    }

    /** Deletes the chat; messages cascade via FK, and the FTS index follows via triggers. */
    public void delete(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chats WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void setArchived(long id, boolean archived) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET archived = ? WHERE id = ?")) {
            ps.setInt(1, archived ? 1 : 0);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void assignProject(long id, Long projectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET projectId = ? WHERE id = ?")) {
            setNullableLong(ps, 1, projectId);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public List<Chat> findByProject(long projectId) throws SQLException {
        String sql = ChatRowMapper.selectColumns()
                + "FROM chats WHERE projectId = ? ORDER BY importedAt, id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        }
    }

    public List<Chat> findByTag(long tagId) throws SQLException {
        String sql = ChatRowMapper.SELECT_C_COLUMNS
                + "FROM chats c "
                + "JOIN chatTags ct ON ct.chatId = c.id "
                + "WHERE ct.tagId = ? "
                + "ORDER BY c.importedAt, c.id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        }
    }

    public void updateTitle(long id, String title) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET title = ? WHERE id = ?")) {
            ps.setString(1, title);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public Optional<Chat> findByExternalIdentity(Source source, String externalConversationId)
            throws SQLException {
        if (externalConversationId == null || externalConversationId.isBlank()) {
            return Optional.empty();
        }
        String sql = ChatRowMapper.selectColumns() + "FROM chats WHERE source = ? AND externalConversationId = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source.dbValue());
            ps.setString(2, externalConversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(ChatRowMapper.read(rs));
            }
        }
    }

    public Map<String, Long> findImportedIdsByExternalIdentity(
            Collection<ConversationCandidate> candidates) throws SQLException {
        Map<String, Long> importedIds = new HashMap<>();
        if (candidates.isEmpty()) {
            return importedIds;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT source, externalConversationId, id FROM chats "
                        + "WHERE externalConversationId IS NOT NULL");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                importedIds.put(identityKey(
                        Source.fromDbValue(rs.getString("source")),
                        rs.getString("externalConversationId")),
                        rs.getLong("id"));
            }
        }
        Map<String, Long> matches = new HashMap<>();
        for (ConversationCandidate candidate : candidates) {
            if (candidate.externalConversationId() == null
                    || candidate.externalConversationId().isBlank()) {
                continue;
            }
            String key = identityKey(candidate.source(), candidate.externalConversationId());
            Long id = importedIds.get(key);
            if (id != null) {
                matches.put(key, id);
            }
        }
        return matches;
    }

    public static String identityKey(Source source, String externalConversationId) {
        return source.dbValue() + "\u001f" + externalConversationId;
    }

    public Optional<Chat> findBySourceAndContentHash(Source source, String contentHash)
            throws SQLException {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }
        String sql = ChatRowMapper.selectColumns()
                + "FROM chats WHERE source = ? AND externalConversationId IS NULL AND contentHash = ? "
                + "ORDER BY importedAt, id LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source.dbValue());
            ps.setString(2, contentHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(ChatRowMapper.read(rs));
            }
        }
    }

    public Chat updateImportMetadata(long id, String title, String sourceUri, String contentHash,
            String sourceUpdatedAt, String lastImportedAt) throws SQLException {
        String sql = "UPDATE chats SET title = ?, sourceUri = ?, contentHash = ?, sourceUpdatedAt = ?, "
                + "lastImportedAt = ?, updatedAt = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, sourceUri);
            ps.setString(3, contentHash);
            ps.setString(4, sourceUpdatedAt);
            ps.setString(5, lastImportedAt);
            ps.setString(6, sourceUpdatedAt);
            ps.setLong(7, id);
            ps.executeUpdate();
        }
        return findById(id).orElseThrow();
    }

    public Chat updateFromSource(long id, Source source, String title, String createdAt, String updatedAt,
            String externalConversationId, String sourceUri, String contentHash,
            String sourceUpdatedAt, String lastImportedAt) throws SQLException {
        String sql = "UPDATE chats SET source = ?, title = ?, createdAt = ?, updatedAt = ?, "
                + "externalConversationId = ?, sourceUri = ?, contentHash = ?, sourceUpdatedAt = ?, "
                + "lastImportedAt = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source.dbValue());
            ps.setString(2, title);
            ps.setString(3, createdAt);
            ps.setString(4, updatedAt);
            ps.setString(5, externalConversationId);
            ps.setString(6, sourceUri);
            ps.setString(7, contentHash);
            ps.setString(8, sourceUpdatedAt);
            ps.setString(9, lastImportedAt);
            ps.setLong(10, id);
            ps.executeUpdate();
        }
        return findById(id).orElseThrow();
    }

    private static List<Chat> readAll(ResultSet rs) throws SQLException {
        List<Chat> chats = new ArrayList<>();
        while (rs.next()) {
            chats.add(ChatRowMapper.read(rs));
        }
        return chats;
    }

    private static void setNullableLong(PreparedStatement ps, int parameter, Long value)
            throws SQLException {
        if (value == null) {
            ps.setNull(parameter, java.sql.Types.INTEGER);
        } else {
            ps.setLong(parameter, value);
        }
    }
}
