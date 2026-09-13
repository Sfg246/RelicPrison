package site.mcrelicworld.relicprison.mining;

import site.mcrelicworld.relicprison.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BulkMiningTransactionRepository {
    private final DatabaseManager database;

    public BulkMiningTransactionRepository(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Boolean> create(BulkMiningTransactionRecord record) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rp_bulk_mining_transactions(transaction_id,player_uuid,source,mine_id,state,"
                            + "block_count,consumed_count,reward_package_id,block_snapshots,reward_payload,"
                            + "commit_payload,spawned_entity_ids,created_at,updated_at,recovery_attempts,failure_summary) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                bind(statement, record);
                return statement.executeUpdate() == 1;
            } catch (SQLException ex) {
                if (duplicate(ex)) return false;
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    public CompletableFuture<Boolean> markState(String transactionId, BulkMiningTransactionState state,
                                                int consumedCount, String rewardPackageId, String failure) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_bulk_mining_transactions SET state=?,consumed_count=?,reward_package_id=?,"
                            + "updated_at=?,failure_summary=? WHERE transaction_id=?")) {
                statement.setString(1, state.name());
                statement.setInt(2, Math.max(0, consumedCount));
                statement.setString(3, rewardPackageId == null ? "" : rewardPackageId);
                statement.setLong(4, System.currentTimeMillis());
                statement.setString(5, truncate(failure));
                statement.setString(6, transactionId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> markRewardDelivered(String transactionId, int consumedCount,
                                                           String rewardPackageId, String rewardPayload,
                                                           String commitPayload,
                                                           List<UUID> spawnedEntityIds) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_bulk_mining_transactions SET state=?,consumed_count=?,reward_package_id=?,"
                            + "reward_payload=?,commit_payload=?,spawned_entity_ids=?,updated_at=?,failure_summary='' "
                            + "WHERE transaction_id=? AND state='REWARD_DELIVERING'")) {
                statement.setString(1, BulkMiningTransactionState.REWARD_DELIVERED.name());
                statement.setInt(2, Math.max(0, consumedCount));
                statement.setString(3, rewardPackageId == null ? "" : rewardPackageId);
                statement.setString(4, rewardPayload == null ? "" : rewardPayload);
                statement.setString(5, commitPayload == null ? "" : commitPayload);
                statement.setString(6, encodeEntityIds(spawnedEntityIds));
                statement.setLong(7, System.currentTimeMillis());
                statement.setString(8, transactionId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> markRewardDelivered(String transactionId, int consumedCount,
                                                           String rewardPackageId, String rewardPayload,
                                                           List<UUID> spawnedEntityIds) {
        return markRewardDelivered(transactionId, consumedCount, rewardPackageId, rewardPayload, "",
                spawnedEntityIds);
    }

    public CompletableFuture<Integer> markRecoverable(String transactionId, String failure) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_bulk_mining_transactions SET state=?,updated_at=?,recovery_attempts=recovery_attempts+1,"
                            + "failure_summary=? WHERE transaction_id=? AND state NOT IN ('COMMITTED','ROLLED_BACK')")) {
                statement.setString(1, BulkMiningTransactionState.FAILED_RECOVERABLE.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, truncate(failure));
                statement.setString(4, transactionId);
                return statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<BulkMiningTransactionRecord>> incomplete(int limit) {
        return database.submitIdempotent(connection -> {
            List<BulkMiningTransactionRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rp_bulk_mining_transactions WHERE state NOT IN ('COMMITTED','ROLLED_BACK',"
                            + "'FAILED_PERMANENT') ORDER BY updated_at ASC LIMIT ?")) {
                statement.setInt(1, Math.max(1, Math.min(250, limit)));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) records.add(read(result));
                }
            }
            return List.copyOf(records);
        });
    }

    public CompletableFuture<Long> countIncomplete() {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM rp_bulk_mining_transactions WHERE state NOT IN "
                            + "('COMMITTED','ROLLED_BACK','FAILED_PERMANENT')");
                 ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        });
    }

    private static void bind(PreparedStatement statement, BulkMiningTransactionRecord record) throws SQLException {
        statement.setString(1, record.transactionId());
        statement.setString(2, record.playerId().toString());
        statement.setString(3, record.source());
        statement.setString(4, record.mineId());
        statement.setString(5, record.state().name());
        statement.setInt(6, record.blockCount());
        statement.setInt(7, record.consumedCount());
        statement.setString(8, record.rewardPackageId());
        statement.setString(9, BulkBlockSnapshot.encodeList(record.blockSnapshots()));
        statement.setString(10, record.rewardPayload());
        statement.setString(11, record.commitPayload());
        statement.setString(12, encodeEntityIds(record.spawnedEntityIds()));
        statement.setLong(13, record.createdAt());
        statement.setLong(14, record.updatedAt());
        statement.setInt(15, record.recoveryAttempts());
        statement.setString(16, truncate(record.failureSummary()));
    }

    private static BulkMiningTransactionRecord read(ResultSet result) throws SQLException {
        return new BulkMiningTransactionRecord(
                result.getString("transaction_id"),
                UUID.fromString(result.getString("player_uuid")),
                result.getString("source"),
                result.getString("mine_id"),
                BulkMiningTransactionState.valueOf(result.getString("state")),
                result.getInt("block_count"),
                result.getInt("consumed_count"),
                result.getString("reward_package_id"),
                BulkBlockSnapshot.decodeList(result.getString("block_snapshots")),
                result.getString("reward_payload"),
                result.getString("commit_payload"),
                decodeEntityIds(result.getString("spawned_entity_ids")),
                result.getLong("created_at"),
                result.getLong("updated_at"),
                result.getInt("recovery_attempts"),
                result.getString("failure_summary"));
    }

    private static boolean duplicate(SQLException ex) {
        return "23000".equals(ex.getSQLState()) || String.valueOf(ex.getMessage()).toLowerCase().contains("unique")
                || String.valueOf(ex.getMessage()).toLowerCase().contains("constraint");
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static String encodeEntityIds(List<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) return "";
        return entityIds.stream().map(UUID::toString).distinct().collect(java.util.stream.Collectors.joining(","));
    }

    private static List<UUID> decodeEntityIds(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        List<UUID> result = new ArrayList<>();
        for (String value : encoded.split(",")) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) { }
        }
        return List.copyOf(result);
    }
}
