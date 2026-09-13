package site.mcrelicworld.relicprison.admin;

import site.mcrelicworld.relicprison.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AuditRepository {
    private final DatabaseManager database;

    public AuditRepository(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Void> record(AuditEntry entry) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rp_staff_audit(audit_id,timestamp,staff_uuid,staff_name,action_type,target_type,"
                            + "target_id,before_summary,after_summary,success,reason,related_id,metadata) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, entry.auditId());
                statement.setLong(2, entry.timestamp());
                statement.setString(3, entry.staffId() == null ? null : entry.staffId().toString());
                statement.setString(4, entry.staffName());
                statement.setString(5, entry.actionType());
                statement.setString(6, entry.targetType());
                statement.setString(7, entry.targetId());
                statement.setString(8, entry.beforeSummary());
                statement.setString(9, entry.afterSummary());
                statement.setBoolean(10, entry.success());
                statement.setString(11, entry.reason());
                statement.setString(12, entry.relatedId());
                statement.setString(13, entry.metadata());
                statement.executeUpdate();
                return null;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    public CompletableFuture<List<AuditEntry>> query(AuditQuery query) {
        return database.submitIdempotent(connection -> {
            List<Object> parameters = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM rp_staff_audit WHERE 1=1");
            query.staffId().ifPresent(value -> {
                sql.append(" AND staff_uuid=?");
                parameters.add(value.toString());
            });
            query.actionType().ifPresent(value -> {
                sql.append(" AND action_type=?");
                parameters.add(value);
            });
            query.targetType().ifPresent(value -> {
                sql.append(" AND target_type=?");
                parameters.add(value);
            });
            query.targetId().ifPresent(value -> {
                sql.append(" AND target_id=?");
                parameters.add(value);
            });
            query.from().ifPresent(value -> {
                sql.append(" AND timestamp>=?");
                parameters.add(value);
            });
            query.to().ifPresent(value -> {
                sql.append(" AND timestamp<=?");
                parameters.add(value);
            });
            sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
            parameters.add(query.pageSize());
            parameters.add((query.page() - 1) * query.pageSize());
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bind(statement, parameters);
                try (ResultSet result = statement.executeQuery()) {
                    List<AuditEntry> entries = new ArrayList<>();
                    while (result.next()) entries.add(read(result));
                    return List.copyOf(entries);
                }
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object value = parameters.get(index);
            if (value instanceof String string) statement.setString(index + 1, string);
            else if (value instanceof Integer integer) statement.setInt(index + 1, integer);
            else if (value instanceof Long number) statement.setLong(index + 1, number);
            else statement.setObject(index + 1, value);
        }
    }

    private static AuditEntry read(ResultSet result) throws SQLException {
        String staff = result.getString("staff_uuid");
        return new AuditEntry(result.getString("audit_id"), result.getLong("timestamp"),
                staff == null || staff.isBlank() ? null : UUID.fromString(staff),
                result.getString("staff_name"), result.getString("action_type"),
                result.getString("target_type"), result.getString("target_id"),
                result.getString("before_summary"), result.getString("after_summary"),
                result.getBoolean("success"), result.getString("reason"),
                result.getString("related_id"), result.getString("metadata"));
    }
}
