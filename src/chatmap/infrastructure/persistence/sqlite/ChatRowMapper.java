package chatmap.infrastructure.persistence.sqlite;

import java.sql.ResultSet;
import java.sql.SQLException;

import chatmap.domain.Chat;
import chatmap.domain.ChatOrigin;
import chatmap.domain.ImportMetadata;
import chatmap.domain.Source;

final class ChatRowMapper {
    static final String SELECT_COLUMNS =
            "SELECT id, projectId, source, title, createdAt, updatedAt, importedAt, archived, "
            + "externalConversationId, sourceUri, contentHash, sourceUpdatedAt, lastImportedAt, originatedBy ";

    static final String SELECT_C_COLUMNS =
            "SELECT c.id, c.projectId, c.source, c.title, c.createdAt, c.updatedAt, "
            + "c.importedAt, c.archived, c.externalConversationId, c.sourceUri, "
            + "c.contentHash, c.sourceUpdatedAt, c.lastImportedAt, c.originatedBy ";

    private ChatRowMapper() {
    }

    static String selectColumns() {
        return SELECT_COLUMNS;
    }

    static Chat read(ResultSet rs) throws SQLException {
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
                new ImportMetadata(
                        rs.getString("externalConversationId"),
                        rs.getString("sourceUri"),
                        rs.getString("contentHash"),
                        rs.getString("sourceUpdatedAt"),
                        rs.getString("lastImportedAt")),
                ChatOrigin.fromDbValue(rs.getString("originatedBy")));
    }
}
