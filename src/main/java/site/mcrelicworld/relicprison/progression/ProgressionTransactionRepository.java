package site.mcrelicworld.relicprison.progression;

import site.mcrelicworld.relicprison.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProgressionTransactionRepository {
    private static final String[] TERMINAL_STATES = {
            ProgressionTransactionState.COMPLETED.name(),
            ProgressionTransactionState.FAILED.name(),
            ProgressionTransactionState.MANUAL_REVIEW.name(),
            ProgressionTransactionState.STAFF_REVIEW.name()
    };

    private final DatabaseManager database;

    public ProgressionTransactionRepository(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<CreateResult> createIfNoActive(ProgressionTransactionRecord record) {
        return database.submitIdempotent(connection -> {
            ProgressionTransactionRecord active = findActive(connection, record.playerId()).orElse(null);
            if (active != null) return new CreateResult(false, active);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rp_progression_transactions(transaction_id,player_uuid,operation_type," +
                            "previous_rank,previous_prestige,target_rank,target_prestige,expected_cost,actual_withdrawn," +
                            "created_at,updated_at,state,recovery_attempts,failure_summary,reward_status,luckperms_status," +
                            "idempotency_key,metadata) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                bindInsert(statement, record);
                statement.executeUpdate();
                return new CreateResult(true, record);
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    public CompletableFuture<Optional<ProgressionTransactionRecord>> findById(String transactionId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rp_progression_transactions WHERE transaction_id=?")) {
                statement.setString(1, transactionId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(read(result)) : Optional.<ProgressionTransactionRecord>empty();
                }
            }
        });
    }

    public CompletableFuture<List<ProgressionTransactionRecord>> pending(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return database.submitIdempotent(connection -> {
            List<ProgressionTransactionRecord> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rp_progression_transactions WHERE state NOT IN (?,?,?,?) " +
                            "ORDER BY created_at ASC LIMIT ?")) {
                bindTerminal(statement);
                statement.setInt(5, safeLimit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(read(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<List<ProgressionTransactionRecord>> pendingFor(UUID playerId) {
        return database.submitIdempotent(connection -> {
            List<ProgressionTransactionRecord> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rp_progression_transactions WHERE player_uuid=? AND state NOT IN (?,?,?,?) " +
                            "ORDER BY created_at ASC")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, TERMINAL_STATES[0]);
                statement.setString(3, TERMINAL_STATES[1]);
                statement.setString(4, TERMINAL_STATES[2]);
                statement.setString(5, TERMINAL_STATES[3]);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(read(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<Void> markWithdrawn(String transactionId, BigDecimal amount) {
        return update(transactionId, ProgressionTransactionState.MONEY_WITHDRAWN,
                "actual_withdrawn=?", amount == null ? BigDecimal.ZERO : amount);
    }

    public CompletableFuture<Void> markValidated(String transactionId) {
        return update(transactionId, ProgressionTransactionState.VALIDATED, null);
    }

    public CompletableFuture<Void> markWithdrawalIntent(String transactionId) {
        return update(transactionId, ProgressionTransactionState.WITHDRAWAL_INTENT_RECORDED, null);
    }

    public CompletableFuture<Void> markWithdrawalInProgress(String transactionId) {
        return update(transactionId, ProgressionTransactionState.WITHDRAWAL_IN_PROGRESS, null);
    }

    public CompletableFuture<Void> markWithdrawalConfirmed(String transactionId, BigDecimal amount) {
        return update(transactionId, ProgressionTransactionState.WITHDRAWAL_CONFIRMED,
                "actual_withdrawn=?", amount == null ? BigDecimal.ZERO : amount);
    }

    public CompletableFuture<Void> markWithdrawalAmbiguous(String transactionId, String summary) {
        return markTerminal(transactionId, ProgressionTransactionState.STAFF_REVIEW,
                summary, ProgressionExternalStatus.MANUAL_REVIEW, ProgressionRewardStatus.MANUAL_REVIEW);
    }

    public CompletableFuture<Void> markProfileUpdatePending(String transactionId) {
        return update(transactionId, ProgressionTransactionState.PROFILE_UPDATE_PENDING, null);
    }

    public CompletableFuture<Void> markPermissionsUpdatePending(String transactionId) {
        return update(transactionId, ProgressionTransactionState.PERMISSIONS_UPDATE_PENDING, null);
    }

    public CompletableFuture<Void> markRewardsPending(String transactionId) {
        return update(transactionId, ProgressionTransactionState.REWARDS_PENDING, null);
    }

    public CompletableFuture<Void> markProfileSaved(String transactionId) {
        return update(transactionId, ProgressionTransactionState.PROFILE_SAVED, null);
    }

    public CompletableFuture<Void> markPermissionsUpdated(String transactionId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET state=?,luckperms_status=?,updated_at=? WHERE transaction_id=?")) {
                statement.setString(1, ProgressionTransactionState.PERMISSIONS_UPDATED.name());
                statement.setString(2, ProgressionExternalStatus.UPDATED.name());
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, transactionId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> reserveRewards(String transactionId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET reward_status=?,updated_at=? " +
                            "WHERE transaction_id=? AND reward_status=?")) {
                statement.setString(1, ProgressionRewardStatus.RESERVED.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, transactionId);
                statement.setString(4, ProgressionRewardStatus.PENDING.name());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Void> markRewardsDelivered(String transactionId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET reward_status=?,updated_at=? WHERE transaction_id=?")) {
                statement.setString(1, ProgressionRewardStatus.DELIVERED.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, transactionId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    public CompletableFuture<Void> markRewardsQueued(String transactionId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET reward_status=?,updated_at=? WHERE transaction_id=?")) {
                statement.setString(1, ProgressionRewardStatus.QUEUED.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, transactionId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    public CompletableFuture<Void> markRewardsSkipped(String transactionId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET reward_status=?,updated_at=? WHERE transaction_id=?")) {
                statement.setString(1, ProgressionRewardStatus.SKIPPED.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, transactionId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    public CompletableFuture<Void> markCompleted(String transactionId) {
        return update(transactionId, ProgressionTransactionState.COMPLETED, null);
    }

    public CompletableFuture<Void> markFailed(String transactionId, String summary) {
        return markTerminal(transactionId, ProgressionTransactionState.FAILED, summary,
                ProgressionExternalStatus.FAILED, null);
    }

    public CompletableFuture<Void> markManualReview(String transactionId, String summary) {
        return markTerminal(transactionId, ProgressionTransactionState.MANUAL_REVIEW, summary,
                ProgressionExternalStatus.MANUAL_REVIEW, ProgressionRewardStatus.MANUAL_REVIEW);
    }

    public CompletableFuture<Void> recordRefundIntent(String transactionId, BigDecimal amount, String reason) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET refund_state=?,refund_amount=?,refund_reason=?,"
                            + "refund_failure_summary='',updated_at=? WHERE transaction_id=?"
                            + " AND refund_state IN ('REFUND_NOT_REQUIRED','REFUND_FAILED')")) {
                statement.setString(1, "REFUND_INTENT_RECORDED");
                statement.setString(2, (amount == null ? BigDecimal.ZERO : amount).toPlainString());
                statement.setString(3, truncate(reason));
                statement.setLong(4, System.currentTimeMillis());
                statement.setString(5, transactionId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> claimRefund(String transactionId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET refund_state=?,refund_attempts=refund_attempts+1,"
                            + "refund_last_attempt_at=?,updated_at=? WHERE transaction_id=?"
                            + " AND refund_state IN ('REFUND_INTENT_RECORDED','REFUND_FAILED')")) {
                long now = System.currentTimeMillis();
                statement.setString(1, "REFUND_IN_PROGRESS");
                statement.setLong(2, now);
                statement.setLong(3, now);
                statement.setString(4, transactionId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Void> markRefundConfirmed(String transactionId) {
        return markRefund(transactionId, "REFUND_CONFIRMED", "");
    }

    public CompletableFuture<Void> markRefundFailed(String transactionId, String summary) {
        return markRefund(transactionId, "REFUND_FAILED", summary);
    }

    public CompletableFuture<Void> markRefundAmbiguous(String transactionId, String summary) {
        return markRefund(transactionId, "REFUND_STAFF_REVIEW", summary);
    }

    public CompletableFuture<Void> incrementRecovery(String transactionId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET recovery_attempts=recovery_attempts+1,updated_at=? " +
                            "WHERE transaction_id=?")) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, transactionId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    private CompletableFuture<Void> markRefund(String transactionId, String refundState, String summary) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET refund_state=?,refund_failure_summary=?,updated_at=?"
                            + " WHERE transaction_id=?")) {
                statement.setString(1, refundState);
                statement.setString(2, truncate(summary));
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, transactionId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    private CompletableFuture<Void> update(String transactionId, ProgressionTransactionState state,
                                           String extraAssignment, Object... values) {
        return database.submitIdempotent(connection -> {
            String extra = extraAssignment == null || extraAssignment.isBlank() ? "" : "," + extraAssignment;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_progression_transactions SET state=?,updated_at=?" + extra + " WHERE transaction_id=?")) {
                int index = 1;
                statement.setString(index++, state.name());
                statement.setLong(index++, System.currentTimeMillis());
                for (Object value : values) {
                    if (value instanceof BigDecimal decimal) statement.setString(index++, decimal.toPlainString());
                    else statement.setObject(index++, value);
                }
                statement.setString(index, transactionId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    private CompletableFuture<Void> markTerminal(String transactionId, ProgressionTransactionState state,
                                                 String summary, ProgressionExternalStatus luckPerms,
                                                 ProgressionRewardStatus rewards) {
        return database.submitIdempotent(connection -> {
            String sql = rewards == null
                    ? "UPDATE rp_progression_transactions SET state=?,failure_summary=?,luckperms_status=?,updated_at=? WHERE transaction_id=?"
                    : "UPDATE rp_progression_transactions SET state=?,failure_summary=?,luckperms_status=?,reward_status=?,updated_at=? WHERE transaction_id=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                statement.setString(index++, state.name());
                statement.setString(index++, truncate(summary));
                statement.setString(index++, luckPerms.name());
                if (rewards != null) statement.setString(index++, rewards.name());
                statement.setLong(index++, System.currentTimeMillis());
                statement.setString(index, transactionId);
                statement.executeUpdate();
                return null;
            }
        });
    }

    private Optional<ProgressionTransactionRecord> findActive(java.sql.Connection connection, UUID playerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM rp_progression_transactions WHERE player_uuid=? AND state NOT IN (?,?,?,?) " +
                        "ORDER BY created_at ASC LIMIT 1")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, TERMINAL_STATES[0]);
            statement.setString(3, TERMINAL_STATES[1]);
            statement.setString(4, TERMINAL_STATES[2]);
            statement.setString(5, TERMINAL_STATES[3]);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        }
    }

    private void bindInsert(PreparedStatement statement, ProgressionTransactionRecord record) throws SQLException {
        int index = 1;
        statement.setString(index++, record.transactionId());
        statement.setString(index++, record.playerId().toString());
        statement.setString(index++, record.operationType().name());
        statement.setString(index++, record.previousRank());
        statement.setString(index++, record.previousPrestige());
        statement.setString(index++, record.targetRank());
        statement.setString(index++, record.targetPrestige());
        statement.setString(index++, record.expectedCost().toPlainString());
        statement.setString(index++, record.actualWithdrawn().toPlainString());
        statement.setLong(index++, record.createdAt());
        statement.setLong(index++, record.updatedAt());
        statement.setString(index++, record.state().name());
        statement.setInt(index++, record.recoveryAttempts());
        statement.setString(index++, truncate(record.failureSummary()));
        statement.setString(index++, record.rewardStatus().name());
        statement.setString(index++, record.luckPermsStatus().name());
        statement.setString(index++, record.idempotencyKey());
        statement.setString(index, record.metadata());
    }

    private static void bindTerminal(PreparedStatement statement) throws SQLException {
        statement.setString(1, TERMINAL_STATES[0]);
        statement.setString(2, TERMINAL_STATES[1]);
        statement.setString(3, TERMINAL_STATES[2]);
        statement.setString(4, TERMINAL_STATES[3]);
    }

    private static ProgressionTransactionRecord read(ResultSet result) throws SQLException {
        return new ProgressionTransactionRecord(
                result.getString("transaction_id"),
                UUID.fromString(result.getString("player_uuid")),
                ProgressionOperationType.valueOf(result.getString("operation_type")),
                result.getString("previous_rank"),
                result.getString("previous_prestige"),
                result.getString("target_rank"),
                result.getString("target_prestige"),
                new BigDecimal(result.getString("expected_cost")),
                new BigDecimal(result.getString("actual_withdrawn")),
                result.getLong("created_at"),
                result.getLong("updated_at"),
                ProgressionTransactionState.valueOf(result.getString("state")),
                result.getInt("recovery_attempts"),
                result.getString("failure_summary"),
                ProgressionRewardStatus.valueOf(result.getString("reward_status")),
                ProgressionExternalStatus.valueOf(result.getString("luckperms_status")),
                result.getString("idempotency_key"),
                result.getString("metadata")
        );
    }

    private static String truncate(String summary) {
        if (summary == null) return "";
        String clean = summary.replace('\n', ' ').replace('\r', ' ');
        return clean.length() <= 512 ? clean : clean.substring(0, 512);
    }

    public record CreateResult(boolean created, ProgressionTransactionRecord record) { }
}
