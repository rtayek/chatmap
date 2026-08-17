package chatmap.application.port.persistence;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import chatmap.domain.Chat;
import chatmap.domain.ConversationCandidate;
import chatmap.domain.Source;

/** Application-facing persistence operations for chats. */
public interface ChatStore {

    TransactionManager transactions();

    Chat insert(Chat chat) throws SQLException;

    Optional<Chat> findById(long id) throws SQLException;

    List<Chat> findAll() throws SQLException;

    Optional<Chat> findMostRecent() throws SQLException;

    void assignProject(long id, Long projectId) throws SQLException;

    List<Chat> findByProject(long projectId) throws SQLException;

    List<Chat> findByTag(long tagId) throws SQLException;

    Optional<Chat> findByExternalIdentity(Source source, String externalConversationId) throws SQLException;

    Optional<Chat> findByPromptSession(String providerId, String modelTargetId, String providerSessionId)
            throws SQLException;

    List<String> findPromptSessions(String providerId, String modelTargetId) throws SQLException;

    Map<String, Long> findImportedIdsByExternalIdentity(Collection<ConversationCandidate> candidates)
            throws SQLException;

    Optional<Chat> findBySourceAndContentHash(Source source, String contentHash) throws SQLException;

    Chat updateImportMetadata(long id, String title, String sourceUri, String contentHash,
            String sourceUpdatedAt, String lastImportedAt) throws SQLException;

    Chat updateFromSource(long id, Source source, String title, String createdAt, String updatedAt,
            String externalConversationId, String sourceUri, String contentHash,
            String sourceUpdatedAt, String lastImportedAt) throws SQLException;

    boolean isUniqueConstraintError(SQLException failure);

    static String identityKey(Source source, String externalConversationId) {
        return source.dbValue() + "\u001f" + externalConversationId;
    }
}
