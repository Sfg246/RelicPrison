package site.mcrelicworld.relicprison.reward;

import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RewardLedgerRepository {
    private static final String CLAIMABLE_STATES = "('PENDING','RETRY_READY','RETRY','SCHEDULED')";
    private final DatabaseManager database;

    public RewardLedgerRepository(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Boolean> createPackage(RewardPackageRecord rewardPackage,
                                                    List<RewardComponentRecord> components) {
        return database.submitIdempotent(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (packageExists(connection, rewardPackage.packageId())) {
                    connection.rollback();
                    return false;
                }
                insertPackageRecord(connection, rewardPackage);
                for (RewardComponentRecord component : components) {
                    insertComponentRecord(connection, component, database.storageType());
                }
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

    public CompletableFuture<Optional<RewardPackageRecord>> packageById(String packageId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rp_reward_packages WHERE package_id=?")) {
                statement.setString(1, packageId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readPackage(result)) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<List<RewardComponentRecord>> components(String packageId) {
        return records("SELECT * FROM rp_reward_components WHERE package_id=? ORDER BY created_at,component_id",
                packageId);
    }

    public CompletableFuture<List<RewardComponentRecord>> due(long now, int limit) {
        return records("SELECT * FROM rp_reward_components WHERE state IN " + CLAIMABLE_STATES
                + " AND due_at<=? AND next_attempt_at<=? ORDER BY due_at ASC LIMIT ?",
                now, now, safeLimit(limit));
    }

    public CompletableFuture<List<ClaimedComponent>> claimDue(long now, String claimant, long leaseMillis,
                                                             int limit) {
        return database.submitIdempotent(connection -> {
            List<ComponentKey> candidates = componentKeys(connection,
                    "SELECT package_id,component_id FROM rp_reward_components WHERE state IN " + CLAIMABLE_STATES
                            + " AND due_at<=? AND next_attempt_at<=? ORDER BY due_at ASC LIMIT ?",
                    now, now, safeLimit(limit));
            return claimCandidates(connection, candidates, claimant, now, leaseMillis);
        });
    }

    public CompletableFuture<List<ClaimedComponent>> claimPendingFor(UUID playerId, long now, String claimant,
                                                                    long leaseMillis, int limit) {
        return database.submitIdempotent(connection -> {
            List<ComponentKey> candidates = componentKeys(connection,
                    "SELECT package_id,component_id FROM rp_reward_components WHERE player_uuid=?"
                            + " AND state IN " + CLAIMABLE_STATES
                            + " AND due_at<=? AND next_attempt_at<=? ORDER BY due_at ASC LIMIT ?",
                    playerId.toString(), now, now, safeLimit(limit));
            return claimCandidates(connection, candidates, claimant, now, leaseMillis);
        });
    }

    public CompletableFuture<Optional<ClaimedComponent>> claim(String packageId, String componentId,
                                                              String claimant, long now, long leaseMillis) {
        return database.submitIdempotent(connection ->
                claimOne(connection, packageId, componentId, claimant, now, leaseMillis));
    }

    public CompletableFuture<Integer> recoverExpiredClaims(long now, String failure) {
        return database.submitIdempotent(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<String> packages = interruptedPackages(connection,
                        " AND claim_expires_at>0 AND claim_expires_at<=?", now);
                int recovered = resetReplaySafe(connection, failure,
                        " AND claim_expires_at>0 AND claim_expires_at<=?", now, now);
                recovered += markInterruptedAmbiguous(connection, failure,
                        " AND claim_expires_at>0 AND claim_expires_at<=?", now, now);
                for (String packageId : packages) refreshPackageState(connection, packageId);
                connection.commit();
                return recovered;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        });
    }

    public CompletableFuture<List<RewardComponentRecord>> pending(int limit) {
        return records("SELECT * FROM rp_reward_components WHERE state IN "
                + "('PENDING','SCHEDULED','CLAIMED','RUNNING','RETRY','RETRY_READY','AMBIGUOUS')"
                + " ORDER BY due_at ASC LIMIT ?", safeLimit(limit));
    }

    public CompletableFuture<List<RewardComponentRecord>> failed(int limit) {
        return records("SELECT * FROM rp_reward_components WHERE state IN ('FAILED','STAFF_REVIEW','AMBIGUOUS') "
                + "ORDER BY last_attempt_at DESC LIMIT ?", safeLimit(limit));
    }

    public CompletableFuture<List<RewardComponentRecord>> pendingFor(UUID playerId, int limit) {
        return records("SELECT * FROM rp_reward_components WHERE player_uuid=? AND state IN "
                + CLAIMABLE_STATES + " ORDER BY due_at ASC LIMIT ?", playerId.toString(), safeLimit(limit));
    }

    public CompletableFuture<Integer> markRunningAmbiguous(String failure) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_reward_components SET state=?,updated_at=?,failure_summary=?,"
                            + "claim_token='',claimed_by='',claimed_at=0,claim_expires_at=0"
                            + " WHERE state IN ('CLAIMED','RUNNING')")) {
                statement.setString(1, RewardComponentState.AMBIGUOUS.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, truncate(failure));
                return statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<InterruptedRecovery> recoverInterruptedComponents(String failure) {
        return database.submitIdempotent(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<String> packages = interruptedPackages(connection, "");
                long now = System.currentTimeMillis();
                int retryable = resetReplaySafe(connection, failure, "", now);
                int assumedDelivered = 0;
                int ambiguous = markInterruptedAmbiguous(connection, failure, "", now);
                for (String packageId : packages) refreshPackageState(connection, packageId);
                connection.commit();
                return new InterruptedRecovery(retryable, assumedDelivered, ambiguous);
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        });
    }

    private static List<String> interruptedPackages(java.sql.Connection connection, String extra,
                                                    Object... parameters) throws SQLException {
        List<String> packages = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT DISTINCT package_id FROM rp_reward_components WHERE state IN ('CLAIMED','RUNNING')" + extra)) {
            bindTail(select, parameters);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) packages.add(rows.getString(1));
            }
        }
        return packages;
    }

    private static int resetReplaySafe(java.sql.Connection connection, String failure, String extra,
                                       long now, Object... parameters) throws SQLException {
        int updated;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_reward_components SET state=?,next_attempt_at=?,updated_at=?,failure_summary=?,"
                        + "claim_token='',claimed_by='',claimed_at=0,claim_expires_at=0"
                        + " WHERE state IN ('CLAIMED','RUNNING') AND source_system='bulk-mining'"
                        + " AND component_type IN ('MONEY','EXPERIENCE')" + extra)) {
            statement.setString(1, RewardComponentState.RETRY_READY.name());
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setString(4, truncate(failure));
            bindTail(statement, 5, parameters);
            updated = statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_reward_components SET state=?,next_attempt_at=?,updated_at=?,failure_summary=?,"
                        + "claim_token='',claimed_by='',claimed_at=0,claim_expires_at=0"
                        + " WHERE state='CLAIMED' AND source_system='bulk-mining'"
                        + " AND component_type IN ('CONSOLE_COMMAND','KEY_COMMAND','PLAYER_COMMAND')" + extra)) {
            statement.setString(1, RewardComponentState.RETRY_READY.name());
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setString(4, truncate(failure));
            bindTail(statement, 5, parameters);
            updated += statement.executeUpdate();
        }
        return updated;
    }

    private static int markInterruptedAmbiguous(java.sql.Connection connection, String failure, String extra,
                                                long now, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_reward_components SET state=?,updated_at=?,failure_summary=?,"
                        + "claim_token='',claimed_by='',claimed_at=0,claim_expires_at=0"
                        + " WHERE state IN ('CLAIMED','RUNNING')" + extra)) {
            statement.setString(1, RewardComponentState.AMBIGUOUS.name());
            statement.setLong(2, now);
            statement.setString(3, truncate(failure));
            bindTail(statement, 4, parameters);
            return statement.executeUpdate();
        }
    }

    private static void bindTail(PreparedStatement statement, Object... parameters) throws SQLException {
        bindTail(statement, 1, parameters);
    }

    private static void bindTail(PreparedStatement statement, int start, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) statement.setObject(start + index, parameters[index]);
    }

    public CompletableFuture<Optional<RewardComponentRecord>> component(String packageId, String componentId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rp_reward_components WHERE package_id=? AND component_id=?")) {
                statement.setString(1, packageId);
                statement.setString(2, componentId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readComponent(result)) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Boolean> markClaimRunning(ClaimedComponent claim) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_reward_components SET state=?,updated_at=? WHERE package_id=?"
                            + " AND component_id=? AND claim_token=? AND state=?")) {
                statement.setString(1, RewardComponentState.RUNNING.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, claim.component().packageId());
                statement.setString(4, claim.component().componentId());
                statement.setString(5, claim.claimToken());
                statement.setString(6, RewardComponentState.CLAIMED.name());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> completeClaim(ClaimedComponent claim, RewardComponentState state,
                                                   String failure, long nextAttemptAt) {
        return database.submitIdempotent(connection -> {
            boolean updated = completeClaim(connection, claim, state, failure, nextAttemptAt);
            if (updated) refreshPackageState(connection, claim.component().packageId());
            return updated;
        });
    }

    public CompletableFuture<Boolean> prepareRetry(String packageId, String componentId, String reason, long now) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_reward_components SET state=?,next_attempt_at=?,updated_at=?,failure_summary=?,"
                            + "claim_token='',claimed_by='',claimed_at=0,claim_expires_at=0"
                            + " WHERE package_id=? AND component_id=?"
                            + " AND state NOT IN ('DELIVERED','CANCELED','CLAIMED','RUNNING')")) {
                statement.setString(1, RewardComponentState.RETRY_READY.name());
                statement.setLong(2, now);
                statement.setLong(3, now);
                statement.setString(4, truncate(reason));
                statement.setString(5, packageId);
                statement.setString(6, componentId);
                boolean updated = statement.executeUpdate() == 1;
                if (updated) refreshPackageState(connection, packageId);
                return updated;
            }
        });
    }

    public CompletableFuture<Void> mark(String packageId, String componentId, RewardComponentState state,
                                        int attempts, String failure) {
        return database.submitIdempotent(connection -> {
            long now = System.currentTimeMillis();
            long completed = terminal(state) ? now : 0L;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_reward_components SET state=?,attempt_count=?,last_attempt_at=?,completed_at=?,"
                            + "delivered_at=?,updated_at=?,failure_summary=?,claim_token='',claimed_by='',"
                            + "claimed_at=0,claim_expires_at=0,next_attempt_at=?"
                            + " WHERE package_id=? AND component_id=?")) {
                statement.setString(1, state.name());
                statement.setInt(2, attempts);
                statement.setLong(3, now);
                statement.setLong(4, completed);
                statement.setLong(5, state == RewardComponentState.DELIVERED ? now : 0L);
                statement.setLong(6, now);
                statement.setString(7, truncate(failure));
                statement.setLong(8, retryLike(state) ? now : 0L);
                statement.setString(9, packageId);
                statement.setString(10, componentId);
                statement.executeUpdate();
                refreshPackageState(connection, packageId);
                return null;
            }
        });
    }

    public CompletableFuture<Void> markPackage(String packageId, RewardComponentState state, String failure) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_reward_packages SET state=?,updated_at=?,failure_summary=? WHERE package_id=?")) {
                statement.setString(1, state.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, truncate(failure));
                statement.setString(4, packageId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    private CompletableFuture<List<RewardComponentRecord>> records(String sql, Object... parameters) {
        return database.submitIdempotent(connection -> {
            List<RewardComponentRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                    bind(statement, index + 1, parameters[index]);
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) records.add(readComponent(result));
                }
                return List.copyOf(records);
            }
        });
    }

    private List<ClaimedComponent> claimCandidates(Connection connection, List<ComponentKey> candidates,
                                                  String claimant, long now, long leaseMillis)
            throws SQLException {
        List<ClaimedComponent> claimed = new ArrayList<>();
        for (ComponentKey candidate : candidates) {
            claimOne(connection, candidate.packageId(), candidate.componentId(), claimant, now, leaseMillis)
                    .ifPresent(claimed::add);
        }
        return List.copyOf(claimed);
    }

    private Optional<ClaimedComponent> claimOne(Connection connection, String packageId, String componentId,
                                               String claimant, long now, long leaseMillis)
            throws SQLException {
        String token = UUID.randomUUID().toString();
        long expiresAt = now + Math.max(1000L, leaseMillis);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_reward_components SET state=?,claim_token=?,claimed_by=?,claimed_at=?,"
                        + "claim_expires_at=?,attempt_count=attempt_count+1,last_attempt_at=?,updated_at=?"
                        + " WHERE package_id=? AND component_id=? AND state IN " + CLAIMABLE_STATES
                        + " AND due_at<=? AND next_attempt_at<=?")) {
            statement.setString(1, RewardComponentState.CLAIMED.name());
            statement.setString(2, token);
            statement.setString(3, truncate(claimant));
            statement.setLong(4, now);
            statement.setLong(5, expiresAt);
            statement.setLong(6, now);
            statement.setLong(7, now);
            statement.setString(8, packageId);
            statement.setString(9, componentId);
            statement.setLong(10, now);
            statement.setLong(11, now);
            if (statement.executeUpdate() != 1) return Optional.empty();
        }
        refreshPackageState(connection, packageId);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM rp_reward_components WHERE package_id=? AND component_id=? AND claim_token=?")) {
            statement.setString(1, packageId);
            statement.setString(2, componentId);
            statement.setString(3, token);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new ClaimedComponent(readComponent(result), token))
                        : Optional.empty();
            }
        }
    }

    private boolean completeClaim(Connection connection, ClaimedComponent claim, RewardComponentState state,
                                  String failure, long nextAttemptAt) throws SQLException {
        long now = System.currentTimeMillis();
        long completed = terminal(state) ? now : 0L;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_reward_components SET state=?,last_attempt_at=?,completed_at=?,delivered_at=?,"
                        + "updated_at=?,failure_summary=?,claim_token='',claimed_by='',claimed_at=0,"
                        + "claim_expires_at=0,next_attempt_at=? WHERE package_id=? AND component_id=?"
                        + " AND claim_token=? AND state IN ('CLAIMED','RUNNING')")) {
            statement.setString(1, state.name());
            statement.setLong(2, now);
            statement.setLong(3, completed);
            statement.setLong(4, state == RewardComponentState.DELIVERED ? now : 0L);
            statement.setLong(5, now);
            statement.setString(6, truncate(failure));
            statement.setLong(7, nextAttemptAt);
            statement.setString(8, claim.component().packageId());
            statement.setString(9, claim.component().componentId());
            statement.setString(10, claim.claimToken());
            return statement.executeUpdate() == 1;
        }
    }

    private void refreshPackageState(Connection connection, String packageId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT state,COUNT(*) AS count FROM rp_reward_components WHERE package_id=? GROUP BY state")) {
            statement.setString(1, packageId);
            int total = 0;
            int terminalCount = 0;
            boolean failed = false;
            boolean review = false;
            boolean active = false;
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    RewardComponentState state = RewardComponentState.valueOf(result.getString("state"));
                    int count = result.getInt("count");
                    total += count;
                    if (state == RewardComponentState.DELIVERED || state == RewardComponentState.CANCELED) {
                        terminalCount += count;
                    } else if (state == RewardComponentState.FAILED) {
                        failed = true;
                    } else if (state == RewardComponentState.STAFF_REVIEW || state == RewardComponentState.AMBIGUOUS) {
                        review = true;
                    } else {
                        active = true;
                    }
                }
            }
            RewardComponentState packageState;
            String failure = "";
            if (total > 0 && terminalCount == total) {
                packageState = RewardComponentState.COMPLETED;
            } else if (review) {
                packageState = RewardComponentState.STAFF_REVIEW;
                failure = "one or more reward components require staff review";
            } else if (failed) {
                packageState = RewardComponentState.PARTIALLY_FAILED;
                failure = "one or more reward components failed";
            } else if (active) {
                packageState = RewardComponentState.IN_PROGRESS;
            } else {
                packageState = RewardComponentState.PENDING;
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE rp_reward_packages SET state=?,updated_at=?,failure_summary=? WHERE package_id=?")) {
                update.setString(1, packageState.name());
                update.setLong(2, System.currentTimeMillis());
                update.setString(3, failure);
                update.setString(4, packageId);
                update.executeUpdate();
            }
        }
    }

    private List<ComponentKey> componentKeys(Connection connection, String sql, Object... parameters)
            throws SQLException {
        List<ComponentKey> keys = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                bind(statement, index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    keys.add(new ComponentKey(result.getString("package_id"), result.getString("component_id")));
                }
            }
        }
        return keys;
    }

    private boolean packageExists(Connection connection, String packageId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT package_id FROM rp_reward_packages WHERE package_id=?")) {
            statement.setString(1, packageId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public static void insertPackageRecord(Connection connection, RewardPackageRecord rewardPackage)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO rp_reward_packages(package_id,source_system,source_operation_id,player_uuid,state,"
                        + "created_at,updated_at,frozen_payload,failure_summary) VALUES(?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, rewardPackage.packageId());
            statement.setString(2, rewardPackage.sourceSystem());
            statement.setString(3, rewardPackage.sourceOperationId());
            statement.setString(4, rewardPackage.playerId() == null ? null : rewardPackage.playerId().toString());
            statement.setString(5, rewardPackage.state().name());
            statement.setLong(6, rewardPackage.createdAt());
            statement.setLong(7, rewardPackage.updatedAt());
            statement.setString(8, rewardPackage.frozenPayload());
            statement.setString(9, truncate(rewardPackage.failureSummary()));
            statement.executeUpdate();
        }
    }

    public static void insertComponentRecord(Connection connection, RewardComponentRecord component,
                                             StorageConfig.Type storageType) throws SQLException {
        boolean mysql = storageType == StorageConfig.Type.MYSQL;
        String sql = mysql
                ? "INSERT IGNORE INTO rp_reward_components(package_id,component_id,source_system,source_operation_id,"
                + "player_uuid,payload,component_type,amount,due_at,state,attempt_count,created_at,updated_at,"
                + "last_attempt_at,completed_at,failure_summary,idempotency_key,claim_token,claimed_by,claimed_at,"
                + "claim_expires_at,next_attempt_at,delivered_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                : "INSERT OR IGNORE INTO rp_reward_components(package_id,component_id,source_system,source_operation_id,"
                + "player_uuid,payload,component_type,amount,due_at,state,attempt_count,created_at,updated_at,"
                + "last_attempt_at,completed_at,failure_summary,idempotency_key,claim_token,claimed_by,claimed_at,"
                + "claim_expires_at,next_attempt_at,delivered_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, component.packageId());
            statement.setString(2, component.componentId());
            statement.setString(3, component.sourceSystem());
            statement.setString(4, component.sourceOperationId());
            statement.setString(5, component.playerId() == null ? null : component.playerId().toString());
            statement.setString(6, component.payload());
            statement.setString(7, component.componentType().name());
            statement.setString(8, component.amount().toPlainString());
            statement.setLong(9, component.dueAt());
            statement.setString(10, component.state().name());
            statement.setInt(11, component.attemptCount());
            statement.setLong(12, component.createdAt());
            statement.setLong(13, component.createdAt());
            statement.setLong(14, component.lastAttemptAt());
            statement.setLong(15, component.completedAt());
            statement.setString(16, truncate(component.failureSummary()));
            statement.setString(17, component.idempotencyKey());
            statement.setString(18, component.claimToken());
            statement.setString(19, truncate(component.claimedBy()));
            statement.setLong(20, component.claimedAt());
            statement.setLong(21, component.claimExpiresAt());
            statement.setLong(22, component.nextAttemptAt());
            statement.setLong(23, component.deliveredAt());
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof String text) statement.setString(index, text);
        else if (value instanceof Integer number) statement.setInt(index, number);
        else if (value instanceof Long number) statement.setLong(index, number);
        else statement.setObject(index, value);
    }

    private static RewardPackageRecord readPackage(ResultSet result) throws SQLException {
        String rawPlayer = result.getString("player_uuid");
        return new RewardPackageRecord(result.getString("package_id"),
                result.getString("source_system"),
                result.getString("source_operation_id"),
                rawPlayer == null || rawPlayer.isBlank() ? null : UUID.fromString(rawPlayer),
                RewardComponentState.valueOf(result.getString("state")),
                result.getLong("created_at"),
                result.getLong("updated_at"),
                result.getString("frozen_payload"),
                result.getString("failure_summary"));
    }

    private static RewardComponentRecord readComponent(ResultSet result) throws SQLException {
        String rawPlayer = result.getString("player_uuid");
        return new RewardComponentRecord(result.getString("package_id"),
                result.getString("component_id"),
                result.getString("source_system"),
                result.getString("source_operation_id"),
                rawPlayer == null || rawPlayer.isBlank() ? null : UUID.fromString(rawPlayer),
                result.getString("payload"),
                RewardComponentType.valueOf(result.getString("component_type")),
                new BigDecimal(result.getString("amount")),
                result.getLong("due_at"),
                RewardComponentState.valueOf(result.getString("state")),
                result.getInt("attempt_count"),
                result.getLong("created_at"),
                result.getLong("last_attempt_at"),
                result.getLong("completed_at"),
                result.getString("failure_summary"),
                result.getString("idempotency_key"),
                valueOrEmpty(result, "claim_token"),
                valueOrEmpty(result, "claimed_by"),
                result.getLong("claimed_at"),
                result.getLong("claim_expires_at"),
                result.getLong("next_attempt_at"),
                result.getLong("delivered_at"));
    }

    private static boolean terminal(RewardComponentState state) {
        return state == RewardComponentState.DELIVERED || state == RewardComponentState.CANCELED;
    }

    private static boolean retryLike(RewardComponentState state) {
        return state == RewardComponentState.RETRY || state == RewardComponentState.RETRY_READY
                || state == RewardComponentState.SCHEDULED || state == RewardComponentState.PENDING;
    }

    private static String valueOrEmpty(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        return value == null ? "" : value;
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(500, limit));
    }

    private static String truncate(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ');
        return clean.length() <= 512 ? clean : clean.substring(0, 512);
    }

    public record ClaimedComponent(RewardComponentRecord component, String claimToken) { }

    public record InterruptedRecovery(int retryable, int assumedDelivered, int ambiguous) { }

    private record ComponentKey(String packageId, String componentId) { }
}
