package chatmap.infrastructure.persistence.sqlite;

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

import chatmap.application.port.persistence.ChatStore;
import chatmap.domain.Chat;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.Source;
import chatmap.application.support.Locks;

/**
 * CRUD for chats. Holds a Connection supplied by the caller; does not own it.
 * (Single shared connection also keeps :memory: test databases coherent.)
 */
public final class ChatRepository implements ChatStore {

    private final Connection conn;
    private final TransactionRunner transactions;

    public ChatRepository(Connection conn) {
        this(conn, new TransactionRunner(conn));
    }

    private ChatRepository(Connection conn, TransactionRunner transactions) {
        this.conn = conn;
        this.transactions = transactions;
    }

    /**
     * The runner serializing transactions on this repository's connection.
     * Services built over this repository share it so their transactional
     * writes and the repositories' per-statement locking use one monitor.
     */
    public TransactionRunner transactions() {
        return transactions;
    }

    private <T, E extends Exception> T locked(Locks.Work<T, E> work) throws E {
        return Locks.locked(conn, work);
    }

    /** Inserts a chat; the id field of the argument is ignored. Returns the stored chat with its new id. */
    public Chat insert(Chat chat) throws SQLException {
        return locked(() -> {
            String sql = "INSERT INTO chats (projectId, source, title, createdAt, updatedAt, importedAt, archived, "
                    + "externalConversationId, sourceUri, contentHash, sourceUpdatedAt, lastImportedAt, originatedBy, "
                    + "providerId, modelTargetId, providerModelName, providerSessionId) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                ps.setString(13, chat.originatedBy().dbValue());
                ps.setString(14, chat.channelId());
                ps.setString(15, chat.modelTargetId());
                ps.setString(16, chat.providerModelName().orElse(null));
                ps.setString(17, chat.providerSessionId());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    long id = keys.getLong(1);
                    return chat.toBuilder().id(id).build();
                }
            }
        });
    }

    public Optional<Chat> findById(long id) throws SQLException {
        return locked(() -> {
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
        });
    }

    public List<Chat> findAll() throws SQLException {
        return locked(() -> {
            String sql = ChatRowMapper.selectColumns()
                    + "FROM chats ORDER BY importedAt, id";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        });
    }

    /** The most recently imported non-archived chat. Archiving a chat removes it from this fallback. */
    public Optional<Chat> findMostRecent() throws SQLException {
        return locked(() -> {
            String sql = ChatRowMapper.selectColumns()
                    + "FROM chats WHERE archived = 0 ORDER BY importedAt DESC, id DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(ChatRowMapper.read(rs));
            }
        });
    }

    /** Deletes the chat and its child messages so message FTS triggers fire, maintaining search index integrity. */
    public void delete(long id) throws SQLException {
        transactions.inTransaction(() -> {
            try (PreparedStatement psMsg = conn.prepareStatement("DELETE FROM messages WHERE chatId = ?");
                    PreparedStatement psChat = conn.prepareStatement("DELETE FROM chats WHERE id = ?")) {
                psMsg.setLong(1, id);
                psMsg.executeUpdate();

                psChat.setLong(1, id);
                psChat.executeUpdate();
            }
            return null;
        });
    }

    public void setArchived(long id, boolean archived) throws SQLException {
        locked(() -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET archived = ? WHERE id = ?")) {
                ps.setInt(1, archived ? 1 : 0);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void assignProject(long id, Long projectId) throws SQLException {
        locked(() -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET projectId = ? WHERE id = ?")) {
                setNullableLong(ps, 1, projectId);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<Chat> findByProject(long projectId) throws SQLException {
        return locked(() -> {
            String sql = ChatRowMapper.selectColumns()
                    + "FROM chats WHERE projectId = ? ORDER BY importedAt, id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    return readAll(rs);
                }
            }
        });
    }

    public List<Chat> findByTag(long tagId) throws SQLException {
        return locked(() -> {
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
        });
    }

    public void updateTitle(long id, String title) throws SQLException {
        locked(() -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE chats SET title = ? WHERE id = ?")) {
                ps.setString(1, title);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public Optional<Chat> findByExternalIdentity(Source source, String externalConversationId)
            throws SQLException {
        return locked(() -> {
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
        });
    }

    @Override
    public Optional<Chat> findByPromptSession(String providerId, String modelTargetId, String providerSessionId)
            throws SQLException {
        return locked(() -> {
            if (providerId == null || providerId.isBlank()
                    || modelTargetId == null || modelTargetId.isBlank()
                    || providerSessionId == null || providerSessionId.isBlank()) {
                return Optional.empty();
            }
            String sql = ChatRowMapper.selectColumns()
                    + "FROM chats WHERE providerId = ? AND modelTargetId = ? AND providerSessionId = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, providerId);
                ps.setString(2, modelTargetId);
                ps.setString(3, providerSessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(ChatRowMapper.read(rs));
                }
            }
        });
    }

    @Override
    public Optional<Chat> findByPromptSession(long projectId, String providerId, String modelTargetId,
            String providerSessionId) throws SQLException {
        return locked(() -> {
            if (projectId <= 0 || providerId == null || providerId.isBlank()
                    || modelTargetId == null || modelTargetId.isBlank()
                    || providerSessionId == null || providerSessionId.isBlank()) {
                return Optional.empty();
            }
            String sql = ChatRowMapper.selectColumns()
                    + "FROM chats WHERE projectId = ? AND providerId = ? "
                    + "AND modelTargetId = ? AND providerSessionId = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, projectId);
                ps.setString(2, providerId);
                ps.setString(3, modelTargetId);
                ps.setString(4, providerSessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(ChatRowMapper.read(rs));
                }
            }
        });
    }

    @Override
    public List<String> findPromptSessions(String providerId, String modelTargetId) throws SQLException {
        return locked(() -> {
            if (providerId == null || providerId.isBlank() || modelTargetId == null || modelTargetId.isBlank()) {
                return List.of();
            }
            String sql = "SELECT DISTINCT providerSessionId FROM chats "
                    + "WHERE providerId = ? AND modelTargetId = ? AND providerSessionId IS NOT NULL "
                    + "AND providerSessionId <> '' ORDER BY providerSessionId";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, providerId);
                ps.setString(2, modelTargetId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> sessions = new ArrayList<>();
                    while (rs.next()) {
                        sessions.add(rs.getString("providerSessionId"));
                    }
                    return sessions;
                }
            }
        });
    }

    public Map<String, Long> findImportedIdsByExternalIdentity(
            Collection<ConversationCandidate> candidates) throws SQLException {
        return locked(() -> {
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
        });
    }

    public static String identityKey(Source source, String externalConversationId) {
        return ChatStore.identityKey(source, externalConversationId);
    }

    /**
     * True if the exception is a violation of one of chats' unique indexes
     * ({@code chatsExternalIdentityIndex} or {@code chatsContentHashIndex}, see
     * {@link Database#applyMigrations}) — i.e. a concurrent writer (e.g. another
     * process sharing the same database file) won a race to insert the same
     * identity or content this caller was also trying to insert. Callers fall
     * back to a lookup instead of failing outright, the same pattern
     * {@link ProjectRepository#isDuplicateNameViolation} uses.
     */
    public static boolean isUniqueConstraintViolation(SQLException e) {
        return e instanceof org.sqlite.SQLiteException sqliteException
                && sqliteException.getResultCode() == org.sqlite.SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE;
    }

    @Override
    public boolean isUniqueConstraintError(SQLException failure) {
        return isUniqueConstraintViolation(failure);
    }

    public Optional<Chat> findBySourceAndContentHash(Source source, String contentHash)
            throws SQLException {
        return locked(() -> {
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
        });
    }

    public Chat updateImportMetadata(long id, String title, String sourceUri, String contentHash,
            String sourceUpdatedAt, String lastImportedAt) throws SQLException {
        return locked(() -> {
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
        });
    }

    public Chat updateFromSource(long id, Source source, String title, String createdAt, String updatedAt,
            String externalConversationId, String sourceUri, String contentHash,
            String sourceUpdatedAt, String lastImportedAt) throws SQLException {
        return locked(() -> {
            String sql = "UPDATE chats SET source = ?, title = ?, createdAt = ?, updatedAt = ?, "
                    + "externalConversationId = ?, sourceUri = ?, contentHash = ?, sourceUpdatedAt = ?, "
                    + "lastImportedAt = ?, originatedBy = ? WHERE id = ?";
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
                // updateFromSource is for imports.
                ps.setString(10, chatmap.domain.ChatOrigin.imported.dbValue());
                ps.setLong(11, id);
                ps.executeUpdate();
            }
            return findById(id).orElseThrow();
        });
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
