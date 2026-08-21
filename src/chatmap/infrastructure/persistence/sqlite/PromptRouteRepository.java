package chatmap.infrastructure.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import chatmap.application.port.persistence.PromptRouteStore;
import chatmap.domain.PromptClassificationLevel;
import chatmap.domain.PromptClassificationReason;
import chatmap.domain.PromptRouteRecord;

/** SQLite persistence for routed prompt metadata. Holds a caller-owned connection. */
public final class PromptRouteRepository implements PromptRouteStore {

    private final Connection conn;

    public PromptRouteRepository(Connection conn) {
        this.conn = conn;
    }

    @Override
    public PromptRouteRecord insert(PromptRouteRecord record) throws SQLException {
        synchronized (conn) {
            String sql = "INSERT INTO promptRoutes (chatId, chatMapProjectIdentity, workingProjectIdentity, "
                    + "conversationId, repositoryPath, classification, classificationConfidence, "
                    + "classificationReasons, routeProviderId, routeModelTargetId, providerModelName, "
                    + "providerSessionId, requestStatus, createdAt) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, record.chatId());
                ps.setString(2, record.chatMapProjectIdentity());
                ps.setString(3, record.workingProjectIdentity());
                ps.setString(4, record.conversationId());
                ps.setString(5, record.repositoryPath().orElse(null));
                ps.setString(6, record.classificationLevel().name());
                ps.setDouble(7, record.classificationConfidence());
                ps.setString(8, encodeReasons(record.classificationReasons()));
                ps.setString(9, record.routeProviderId());
                ps.setString(10, record.routeModelTargetId());
                ps.setString(11, record.providerModelName().orElse(null));
                ps.setString(12, record.providerSessionId().orElse(null));
                ps.setString(13, record.requestStatus());
                ps.setString(14, record.createdAt());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return withId(record, keys.getLong(1));
                }
            }
        }
    }

    @Override
    public Optional<PromptRouteRecord> findByChatId(long chatId) throws SQLException {
        synchronized (conn) {
            String sql = selectColumns() + " WHERE chatId = ? ORDER BY id DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, chatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(read(rs));
                }
            }
        }
    }

    @Override
    public List<PromptRouteRecord> findByWorkingProjectAndConversation(String workingProjectIdentity,
            String conversationId) throws SQLException {
        synchronized (conn) {
            String sql = selectColumns()
                    + " WHERE workingProjectIdentity = ? AND conversationId = ? ORDER BY id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, workingProjectIdentity);
                ps.setString(2, conversationId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<PromptRouteRecord> records = new ArrayList<>();
                    while (rs.next()) {
                        records.add(read(rs));
                    }
                    return records;
                }
            }
        }
    }

    private static PromptRouteRecord withId(PromptRouteRecord record, long id) {
        return new PromptRouteRecord(id, record.chatId(), record.chatMapProjectIdentity(),
                record.workingProjectIdentity(), record.conversationId(), record.repositoryPath(),
                record.classificationLevel(), record.classificationConfidence(), record.classificationReasons(),
                record.routeProviderId(), record.routeModelTargetId(), record.providerModelName(),
                record.providerSessionId(), record.requestStatus(), record.createdAt());
    }

    private static String selectColumns() {
        return "SELECT id, chatId, chatMapProjectIdentity, workingProjectIdentity, conversationId, "
                + "repositoryPath, classification, classificationConfidence, classificationReasons, "
                + "routeProviderId, routeModelTargetId, providerModelName, providerSessionId, "
                + "requestStatus, createdAt FROM promptRoutes";
    }

    private static PromptRouteRecord read(ResultSet rs) throws SQLException {
        return new PromptRouteRecord(
                rs.getLong("id"),
                rs.getLong("chatId"),
                rs.getString("chatMapProjectIdentity"),
                rs.getString("workingProjectIdentity"),
                rs.getString("conversationId"),
                Optional.ofNullable(rs.getString("repositoryPath")),
                PromptClassificationLevel.valueOf(rs.getString("classification")),
                rs.getDouble("classificationConfidence"),
                decodeReasons(rs.getString("classificationReasons")),
                rs.getString("routeProviderId"),
                rs.getString("routeModelTargetId"),
                Optional.ofNullable(rs.getString("providerModelName")),
                Optional.ofNullable(rs.getString("providerSessionId")),
                rs.getString("requestStatus"),
                rs.getString("createdAt"));
    }

    private static String encodeReasons(List<PromptClassificationReason> reasons) {
        return reasons.stream().map(PromptClassificationReason::code).collect(Collectors.joining(","));
    }

    private static List<PromptClassificationReason> decodeReasons(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        return Arrays.stream(encoded.split(","))
                .map(PromptRouteRepository::reasonByCode)
                .toList();
    }

    private static PromptClassificationReason reasonByCode(String code) {
        for (PromptClassificationReason reason : PromptClassificationReason.values()) {
            if (reason.code().equals(code)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown prompt classification reason: " + code);
    }
}
