package chatmap.infrastructure.persistence.sqlite;

import java.sql.ResultSet;
import java.sql.SQLException;

import chatmap.domain.Chat;
import chatmap.domain.Source;

final class ChatRowMapper {
    static final String SELECT_COLUMNS =
            "SELECT id, projectId, source, title, createdAt, updatedAt, importedAt, archived, "
            + "externalConversationId, sourceUri, contentHash, sourceUpdatedAt, lastImportedAt, originatedBy, "
            + "providerId, modelTargetId, providerModelName, providerSessionId ";

    static final String SELECT_C_COLUMNS =
            "SELECT c.id, c.projectId, c.source, c.title, c.createdAt, c.updatedAt, "
            + "c.importedAt, c.archived, c.externalConversationId, c.sourceUri, "
            + "c.contentHash, c.sourceUpdatedAt, c.lastImportedAt, c.originatedBy, "
            + "c.providerId, c.modelTargetId, c.providerModelName, c.providerSessionId ";

    private ChatRowMapper() {
    }

    static String selectColumns() {
        return SELECT_COLUMNS;
    }

    static Chat read(ResultSet rs) throws SQLException {
        long projectId = rs.getLong("projectId");
        Long boxedProjectId = rs.wasNull() ? null : projectId;
        return Chat.builder()
                .id(rs.getLong("id"))
                .projectId(boxedProjectId)
                .source(Source.fromDbValue(rs.getString("source")))
                .title(rs.getString("title"))
                .createdAt(rs.getString("createdAt"))
                .updatedAt(rs.getString("updatedAt"))
                .importedAt(rs.getString("importedAt"))
                .archived(rs.getInt("archived") != 0)
                .externalConversationId(rs.getString("externalConversationId"))
                .sourceUri(rs.getString("sourceUri"))
                .contentHash(rs.getString("contentHash"))
                .sourceUpdatedAt(rs.getString("sourceUpdatedAt"))
                .lastImportedAt(rs.getString("lastImportedAt"))
                .originatedBy(chatmap.domain.ChatOrigin.fromDbValue(rs.getString("originatedBy")))
                .channelId(rs.getString("providerId"))
                .modelTargetId(rs.getString("modelTargetId"))
                .providerModelName(rs.getString("providerModelName"))
                .providerSessionId(rs.getString("providerSessionId"))
                .build();
    }
}
