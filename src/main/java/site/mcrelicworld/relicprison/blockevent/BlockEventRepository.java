package site.mcrelicworld.relicprison.blockevent;

import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.database.DatabaseManager;
import site.mcrelicworld.relicprison.reward.RewardComponentRecord;
import site.mcrelicworld.relicprison.reward.RewardLedgerRepository;
import site.mcrelicworld.relicprison.reward.RewardPackageRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockEventRepository {
    private final DatabaseManager database;

    public BlockEventRepository(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Map<StateKey, State>> loadAll() {
        return database.submitIdempotent(connection -> {
            Map<StateKey, State> result = new ConcurrentHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_block_event_state");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    StateKey key = new StateKey(UUID.fromString(rows.getString("player_uuid")),
                            rows.getString("event_id"), rows.getString("state_key"));
                    result.put(key, new State(rows.getLong("last_triggered_at"), rows.getLong("completed_at"),
                            rows.getLong("counter")));
                }
            }
            return result;
        });
    }

    public CompletableFuture<Void> save(StateKey key, State state) {
        return database.submitIdempotent(connection -> {
            boolean mysql = database.storageType() == StorageConfig.Type.MYSQL;
            String sql = mysql
                    ? "INSERT INTO rp_block_event_state(player_uuid,event_id,state_key,last_triggered_at,completed_at,counter,updated_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE last_triggered_at=VALUES(last_triggered_at),completed_at=VALUES(completed_at),counter=VALUES(counter),updated_at=VALUES(updated_at)"
                    : "INSERT INTO rp_block_event_state(player_uuid,event_id,state_key,last_triggered_at,completed_at,counter,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(player_uuid,event_id,state_key) DO UPDATE SET last_triggered_at=excluded.last_triggered_at,completed_at=excluded.completed_at,counter=excluded.counter,updated_at=excluded.updated_at";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, key.playerId().toString());
                statement.setString(2, key.eventId());
                statement.setString(3, key.stateKey());
                statement.setLong(4, state.lastTriggeredAt());
                statement.setLong(5, state.completedAt());
                statement.setLong(6, state.counter());
                statement.setLong(7, System.currentTimeMillis());
                statement.executeUpdate();
                return null;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    public CompletableFuture<Void> saveAll(Map<StateKey, State> updates) {
        if (updates.isEmpty()) return CompletableFuture.completedFuture(null);
        CompletableFuture<?>[] futures = updates.entrySet().stream()
                .map(entry -> save(entry.getKey(), entry.getValue()))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public CompletableFuture<Boolean> reserveTrigger(TriggerRecord trigger) {
        return database.submitIdempotent(connection -> {
            boolean mysql = database.storageType() == StorageConfig.Type.MYSQL;
            String sql = mysql
                    ? "INSERT IGNORE INTO rp_block_event_triggers(trigger_id,event_id,player_uuid,operation_id,"
                    + "block_identity,trigger_type,period_id,milestone,state,package_id,created_at,updated_at,"
                    + "failure_summary,frozen_payload,logical_claim_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                    : "INSERT OR IGNORE INTO rp_block_event_triggers(trigger_id,event_id,player_uuid,operation_id,"
                    + "block_identity,trigger_type,period_id,milestone,state,package_id,created_at,updated_at,"
                    + "failure_summary,frozen_payload,logical_claim_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, trigger.triggerId());
                statement.setString(2, trigger.eventId());
                statement.setString(3, trigger.playerId().toString());
                statement.setString(4, trigger.operationId());
                statement.setString(5, trigger.blockIdentity());
                statement.setString(6, trigger.triggerType());
                statement.setString(7, trigger.periodId());
                statement.setLong(8, trigger.milestone());
                statement.setString(9, "RESERVED");
                statement.setString(10, "");
                statement.setLong(11, now);
                statement.setLong(12, now);
                statement.setString(13, "");
                statement.setString(14, "");
                statement.setString(15, trigger.logicalClaimKey());
                return statement.executeUpdate() == 1;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    public CompletableFuture<Boolean> reserveFrozenTrigger(TriggerRecord trigger, Map<StateKey, State> updates,
                                                            String frozenPayload) {
        return database.submitIdempotent(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean inserted = insertTrigger(connection, trigger, "RESERVED", "", frozenPayload);
                if (!inserted) {
                    incrementDuplicate(connection, trigger.logicalClaimKey());
                    connection.commit();
                    return false;
                }
                for (Map.Entry<StateKey, State> entry : updates.entrySet()) {
                    upsertState(connection, entry.getKey(), entry.getValue());
                }
                markTrigger(connection, trigger.triggerId(), "PROGRESS_RECORDED", "", "");
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw new DatabaseManager.DatabaseException(ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        });
    }

    public CompletableFuture<TriggerCommitResult> reserveStateAndPackage(TriggerRecord trigger,
                                                                         Map<StateKey, State> updates,
                                                                         RewardPackageRecord rewardPackage,
                                                                         List<RewardComponentRecord> components,
                                                                         String frozenPayload) {
        return database.submitIdempotent(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean inserted = insertTrigger(connection, trigger, "RESERVED", "",
                        frozenPayload == null ? "" : frozenPayload);
                if (!inserted) {
                    incrementDuplicate(connection, trigger.logicalClaimKey());
                    boolean exists = packageExists(connection, rewardPackage.packageId());
                    connection.commit();
                    return new TriggerCommitResult(false, exists, exists ? "already-processed" : "duplicate-reserved");
                }
                for (Map.Entry<StateKey, State> entry : updates.entrySet()) {
                    upsertState(connection, entry.getKey(), entry.getValue());
                }
                markTrigger(connection, trigger.triggerId(), "PROGRESS_RECORDED", "", "");
                RewardLedgerRepository.insertPackageRecord(connection, rewardPackage);
                for (RewardComponentRecord component : components) {
                    RewardLedgerRepository.insertComponentRecord(connection, component, database.storageType());
                }
                markTrigger(connection, trigger.triggerId(), "REWARD_CREATED", rewardPackage.packageId(), "");
                if (!packageExists(connection, rewardPackage.packageId())) {
                    throw new SQLException("Missing block event reward package after insert: "
                            + rewardPackage.packageId());
                }
                markTrigger(connection, trigger.triggerId(), "COMMITTED", rewardPackage.packageId(), "");
                connection.commit();
                return new TriggerCommitResult(true, true, "committed");
            } catch (SQLException ex) {
                connection.rollback();
                throw new DatabaseManager.DatabaseException(ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        });
    }

    public CompletableFuture<Void> markTriggerComplete(String triggerId, String packageId) {
        return database.submitIdempotent(connection -> {
            markTrigger(connection, triggerId, "COMMITTED", packageId == null ? "" : packageId, "");
            return null;
        });
    }

    public CompletableFuture<Boolean> packageExists(String packageId) {
        return database.submitIdempotent(connection -> packageExists(connection, packageId));
    }

    public CompletableFuture<Boolean> createPackageForExistingTrigger(IncompleteTrigger trigger,
                                                                      RewardPackageRecord rewardPackage,
                                                                      List<RewardComponentRecord> components,
                                                                      String frozenPayload) {
        return database.submitIdempotent(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!packageExists(connection, rewardPackage.packageId())) {
                    RewardLedgerRepository.insertPackageRecord(connection, rewardPackage);
                    for (RewardComponentRecord component : components) {
                        RewardLedgerRepository.insertComponentRecord(connection, component, database.storageType());
                    }
                }
                if (!packageExists(connection, rewardPackage.packageId())) {
                    throw new SQLException("Missing block event reward package after recovery insert: "
                            + rewardPackage.packageId());
                }
                markTrigger(connection, trigger.triggerId(), "COMMITTED", rewardPackage.packageId(),
                        "recovery confirmed reward package");
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                markRecoverable(connection, trigger.triggerId(), "recovery failed: " + ex.getMessage());
                connection.commit();
                throw new DatabaseManager.DatabaseException(ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        });
    }

    public CompletableFuture<Void> markRecoverable(String triggerId, String failure) {
        return database.submitIdempotent(connection -> {
            markRecoverable(connection, triggerId, failure);
            return null;
        });
    }

    public CompletableFuture<List<IncompleteTrigger>> incompleteTriggers(int limit) {
        return database.submitIdempotent(connection -> {
            List<IncompleteTrigger> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT trigger_id,event_id,player_uuid,operation_id,block_identity,trigger_type,period_id,"
                            + "milestone,package_id,state,frozen_payload,failure_summary,logical_claim_key,"
                            + "recovery_attempts,created_at "
                            + "FROM rp_block_event_triggers WHERE state NOT IN ('COMMITTED','CANCELLED') "
                            + "ORDER BY updated_at ASC LIMIT ?")) {
                statement.setInt(1, Math.max(1, Math.min(200, limit)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new IncompleteTrigger(rows.getString("trigger_id"), rows.getString("event_id"),
                                UUID.fromString(rows.getString("player_uuid")), rows.getString("operation_id"),
                                rows.getString("block_identity"), rows.getString("trigger_type"),
                                rows.getString("period_id"), rows.getLong("milestone"),
                                rows.getString("package_id"), rows.getString("state"),
                                rows.getString("frozen_payload"), rows.getString("failure_summary"),
                                rows.getString("logical_claim_key"), rows.getInt("recovery_attempts"),
                                rows.getLong("created_at")));
                    }
                }
                return List.copyOf(result);
            }
        });
    }

    public CompletableFuture<Integer> recoverIncompleteTriggers() {
        return database.submitIdempotent(connection -> {
            int recovered = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT trigger_id,package_id FROM rp_block_event_triggers "
                            + "WHERE state NOT IN ('COMMITTED','CANCELLED') LIMIT 200");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String triggerId = rows.getString("trigger_id");
                    String packageId = rows.getString("package_id");
                    if (packageId != null && !packageId.isBlank() && packageExists(connection, packageId)) {
                        markTrigger(connection, triggerId, "COMMITTED", packageId, "startup recovery confirmed package");
                    } else {
                        markRecoverable(connection, triggerId,
                                "missing reward package; staff inspection required");
                    }
                    recovered++;
                }
            }
            return recovered;
        });
    }

    private boolean insertTrigger(Connection connection, TriggerRecord trigger, String state, String packageId,
                                  String frozenPayload) throws SQLException {
        boolean mysql = database.storageType() == StorageConfig.Type.MYSQL;
        String sql = mysql
                    ? "INSERT IGNORE INTO rp_block_event_triggers(trigger_id,event_id,player_uuid,operation_id,"
                    + "block_identity,trigger_type,period_id,milestone,state,package_id,created_at,updated_at,"
                    + "failure_summary,frozen_payload,logical_claim_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                    : "INSERT OR IGNORE INTO rp_block_event_triggers(trigger_id,event_id,player_uuid,operation_id,"
                    + "block_identity,trigger_type,period_id,milestone,state,package_id,created_at,updated_at,"
                    + "failure_summary,frozen_payload,logical_claim_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trigger.triggerId());
            statement.setString(2, trigger.eventId());
            statement.setString(3, trigger.playerId().toString());
            statement.setString(4, trigger.operationId());
            statement.setString(5, trigger.blockIdentity());
            statement.setString(6, trigger.triggerType());
            statement.setString(7, trigger.periodId());
            statement.setLong(8, trigger.milestone());
            statement.setString(9, state);
            statement.setString(10, packageId == null ? "" : packageId);
            statement.setLong(11, now);
            statement.setLong(12, now);
            statement.setString(13, "");
            statement.setString(14, frozenPayload == null ? "" : frozenPayload);
            statement.setString(15, trigger.logicalClaimKey());
            return statement.executeUpdate() == 1;
        }
    }

    private void upsertState(Connection connection, StateKey key, State state) throws SQLException {
        boolean mysql = database.storageType() == StorageConfig.Type.MYSQL;
        String sql = mysql
                ? "INSERT INTO rp_block_event_state(player_uuid,event_id,state_key,last_triggered_at,completed_at,counter,updated_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE last_triggered_at=VALUES(last_triggered_at),completed_at=VALUES(completed_at),counter=VALUES(counter),updated_at=VALUES(updated_at)"
                : "INSERT INTO rp_block_event_state(player_uuid,event_id,state_key,last_triggered_at,completed_at,counter,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(player_uuid,event_id,state_key) DO UPDATE SET last_triggered_at=excluded.last_triggered_at,completed_at=excluded.completed_at,counter=excluded.counter,updated_at=excluded.updated_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.playerId().toString());
            statement.setString(2, key.eventId());
            statement.setString(3, key.stateKey());
            statement.setLong(4, state.lastTriggeredAt());
            statement.setLong(5, state.completedAt());
            statement.setLong(6, state.counter());
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static void markTrigger(Connection connection, String triggerId, String state, String packageId,
                                    String failure) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_block_event_triggers SET state=?,package_id=?,updated_at=?,failure_summary=? "
                        + "WHERE trigger_id=?")) {
            statement.setString(1, state);
            statement.setString(2, packageId == null ? "" : packageId);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, failure == null ? "" : failure);
            statement.setString(5, triggerId);
            statement.executeUpdate();
        }
    }

    private static void markRecoverable(Connection connection, String triggerId, String failure) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_block_event_triggers SET state='FAILED_RECOVERABLE',updated_at=?,"
                        + "failure_summary=?,recovery_attempts=recovery_attempts+1 WHERE trigger_id=?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, failure == null ? "" : failure);
            statement.setString(3, triggerId);
            statement.executeUpdate();
        }
    }

    private static void incrementDuplicate(Connection connection, String logicalClaimKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_block_event_triggers SET duplicate_count=duplicate_count+1,updated_at=? "
                        + "WHERE logical_claim_key=?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, logicalClaimKey == null ? "" : logicalClaimKey);
            statement.executeUpdate();
        }
    }

    private static boolean packageExists(Connection connection, String packageId) throws SQLException {
        if (packageId == null || packageId.isBlank()) return false;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT package_id FROM rp_reward_packages WHERE package_id=?")) {
            statement.setString(1, packageId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    public record StateKey(UUID playerId, String eventId, String stateKey) { }
    public record State(long lastTriggeredAt, long completedAt, long counter) { }
    public record TriggerRecord(String triggerId, String eventId, UUID playerId, String operationId,
                                String blockIdentity, String triggerType, String periodId, long milestone,
                                String logicalClaimKey) { }
    public record TriggerCommitResult(boolean createdTrigger, boolean packageExists, String state) { }
    public record IncompleteTrigger(String triggerId, String eventId, UUID playerId, String operationId,
                                    String blockIdentity, String triggerType, String periodId, long milestone,
                                    String packageId, String state, String frozenPayload, String failureSummary,
                                    String logicalClaimKey, int recoveryAttempts, long createdAt) { }
}
