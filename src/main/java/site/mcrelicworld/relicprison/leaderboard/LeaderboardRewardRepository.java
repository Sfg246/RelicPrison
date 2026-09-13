package site.mcrelicworld.relicprison.leaderboard;

import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.database.DatabaseManager;
import site.mcrelicworld.relicprison.reward.RewardComponentRecord;
import site.mcrelicworld.relicprison.reward.RewardLedgerRepository;
import site.mcrelicworld.relicprison.reward.RewardPackageRecord;

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

public final class LeaderboardRewardRepository {
    private final DatabaseManager database;

    public LeaderboardRewardRepository(DatabaseManager database) { this.database = database; }

    public CompletableFuture<Boolean> reservePeriod(String boardId, String periodId, String metric, String periodType,
                                                    String winnersSnapshot) {
        return database.submitIdempotent(connection -> {
            long now = System.currentTimeMillis();
            boolean mysql = database.storageType() == StorageConfig.Type.MYSQL;
            String insertSql = mysql
                    ? "INSERT IGNORE INTO rp_leaderboard_reward_periods(board_id,period_id,metric,period_type,state,"
                    + "winners_snapshot,created_at,updated_at,finalized_at,announced_at,failure_summary) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?)"
                    : "INSERT OR IGNORE INTO rp_leaderboard_reward_periods(board_id,period_id,metric,period_type,state,"
                    + "winners_snapshot,created_at,updated_at,finalized_at,announced_at,failure_summary) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement insert = connection.prepareStatement(
                    insertSql)) {
                insert.setString(1, boardId);
                insert.setString(2, periodId);
                insert.setString(3, metric);
                insert.setString(4, periodType);
                insert.setString(5, "RESERVED");
                insert.setString(6, winnersSnapshot);
                insert.setLong(7, now);
                insert.setLong(8, now);
                insert.setLong(9, now);
                insert.setLong(10, 0L);
                insert.setString(11, "");
                return insert.executeUpdate() == 1;
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<Boolean> reserveReward(LeaderboardRewardRecord record) {
        return database.submitIdempotent(connection -> {
            boolean mysql = database.storageType() == StorageConfig.Type.MYSQL;
            String sql = mysql
                    ? "INSERT IGNORE INTO rp_leaderboard_reward_ledger(board_id,period_id,player_uuid,final_position,"
                    + "reward_definition_id,delivery_state,attempt_count,created_at,updated_at,failure_summary) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?)"
                    : "INSERT OR IGNORE INTO rp_leaderboard_reward_ledger(board_id,period_id,player_uuid,final_position,"
                    + "reward_definition_id,delivery_state,attempt_count,created_at,updated_at,failure_summary) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                insert.setString(1, record.boardId());
                insert.setString(2, record.periodId());
                insert.setString(3, record.playerId().toString());
                insert.setInt(4, record.finalPosition());
                insert.setString(5, record.rewardDefinitionId());
                insert.setString(6, record.state().name());
                insert.setInt(7, record.attemptCount());
                insert.setLong(8, record.createdAt());
                insert.setLong(9, record.updatedAt());
                insert.setString(10, record.failureSummary());
                return insert.executeUpdate() == 1;
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<FinalizationResult> finalizePeriod(FinalizationPeriod period,
                                                               List<WinnerSnapshot> winners,
                                                               List<RewardBundle> rewards) {
        return database.submitIdempotent(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String effectiveRewardPlan = period.rewardPlan().isBlank()
                        ? freezeRewardBundles(winners, rewards) : period.rewardPlan();
                FinalizationPeriod effectivePeriod = new FinalizationPeriod(period.boardId(), period.periodId(),
                        period.metric(), period.periodType(), period.winnersSnapshot(), effectiveRewardPlan,
                        period.createdAt());
                PeriodState existing = periodState(connection, period.boardId(), period.periodId()).orElse(null);
                if (existing != null) {
                    if ("FINALIZED".equals(existing.state())) {
                        connection.rollback();
                        return new FinalizationResult(false, true, "period already FINALIZED");
                    }
                    String storedPlan = existing.rewardPlan();
                    if (storedPlan.isBlank() && !effectiveRewardPlan.isBlank()) {
                        storeRewardPlan(connection, period.boardId(), period.periodId(), effectiveRewardPlan);
                        for (WinnerSnapshot winner : winners) insertWinner(connection, winner);
                        storedPlan = effectiveRewardPlan;
                    }
                    boolean finalized = recoverPeriod(connection, period.boardId(), period.periodId(), storedPlan);
                    connection.commit();
                    return new FinalizationResult(false, finalized,
                            finalized ? "recovered-finalized" : "recovery-incomplete");
                }
                insertPeriod(connection, effectivePeriod);
                boolean finalized = completePeriod(connection, effectivePeriod, winners, rewards);
                connection.commit();
                return new FinalizationResult(true, finalized, finalized ? "finalized" : "recovery-incomplete");
            } catch (SQLException ex) {
                connection.rollback();
                throw new DatabaseManager.DatabaseException(ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        });
    }

    public CompletableFuture<Void> mark(String boardId, String periodId, UUID playerId, String rewardDefinitionId,
                                        RewardDeliveryState state, int attempts, String failure) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_leaderboard_reward_ledger SET delivery_state=?,attempt_count=?,updated_at=?,"
                            + "failure_summary=? WHERE board_id=? AND period_id=? AND player_uuid=? "
                            + "AND reward_definition_id=?")) {
                statement.setString(1, state.name());
                statement.setInt(2, attempts);
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, failure == null ? "" : failure);
                statement.setString(5, boardId);
                statement.setString(6, periodId);
                statement.setString(7, playerId.toString());
                statement.setString(8, rewardDefinitionId);
                statement.executeUpdate();
                return null;
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<Optional<LeaderboardRewardRecord>> record(String boardId, String periodId, UUID playerId,
                                                                       String rewardDefinitionId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT_LEDGER + " WHERE board_id=? AND period_id=? AND player_uuid=? "
                            + "AND reward_definition_id=?")) {
                statement.setString(1, boardId);
                statement.setString(2, periodId);
                statement.setString(3, playerId.toString());
                statement.setString(4, rewardDefinitionId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(read(result)) : Optional.empty();
                }
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<List<LeaderboardRewardRecord>> history(int limit) {
        return records(SELECT_LEDGER + " ORDER BY updated_at DESC LIMIT ?",
                Math.max(1, Math.min(200, limit)));
    }

    public CompletableFuture<List<LeaderboardRewardRecord>> pendingFor(UUID playerId, int limit) {
        return records(SELECT_LEDGER + " WHERE player_uuid=? AND delivery_state='PENDING_OFFLINE' "
                + "ORDER BY created_at ASC LIMIT ?", playerId.toString(), Math.max(1, Math.min(200, limit)));
    }

    public CompletableFuture<List<LeaderboardRewardRecord>> failedOrPending(int limit) {
        return records(SELECT_LEDGER + " WHERE delivery_state IN "
                + "('RESERVED','PACKAGE_CREATED','PENDING_OFFLINE','RETRY','STAFF_REVIEW') "
                + "ORDER BY updated_at DESC LIMIT ?",
                Math.max(1, Math.min(200, limit)));
    }

    public CompletableFuture<List<IncompletePeriod>> incompletePeriods(int limit) {
        return database.submitIdempotent(connection -> {
            List<IncompletePeriod> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT board_id,period_id,state,reward_package_count,failure_summary,reward_plan "
                            + "FROM rp_leaderboard_reward_periods WHERE state<>'FINALIZED' "
                            + "ORDER BY updated_at ASC LIMIT ?")) {
                statement.setInt(1, Math.max(1, Math.min(200, limit)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        records.add(new IncompletePeriod(rows.getString("board_id"), rows.getString("period_id"),
                                rows.getString("state"), rows.getInt("reward_package_count"),
                                rows.getString("failure_summary"), rows.getString("reward_plan")));
                    }
                }
                return List.copyOf(records);
            }
        });
    }

    public CompletableFuture<Integer> recoverIncompletePeriods(int limit) {
        return database.submitIdempotent(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<IncompletePeriod> periods = incompletePeriods(connection, limit);
                int recovered = 0;
                for (IncompletePeriod period : periods) {
                    recoverPeriod(connection, period.boardId(), period.periodId(), period.rewardPlan());
                    recovered++;
                }
                connection.commit();
                return recovered;
            } catch (SQLException ex) {
                connection.rollback();
                throw new DatabaseManager.DatabaseException(ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        });
    }

    private CompletableFuture<List<LeaderboardRewardRecord>> records(String sql, Object... parameters) {
        return database.submitIdempotent(connection -> {
            List<LeaderboardRewardRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                    Object parameter = parameters[index];
                    if (parameter instanceof String value) statement.setString(index + 1, value);
                    else if (parameter instanceof Integer value) statement.setInt(index + 1, value);
                    else statement.setObject(index + 1, parameter);
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) records.add(read(result));
                }
                return List.copyOf(records);
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    private static LeaderboardRewardRecord read(ResultSet result) throws SQLException {
        return new LeaderboardRewardRecord(result.getString("board_id"), result.getString("period_id"),
                UUID.fromString(result.getString("player_uuid")), result.getInt("final_position"),
                result.getString("reward_definition_id"),
                RewardDeliveryState.valueOf(result.getString("delivery_state")), result.getInt("attempt_count"),
                result.getLong("created_at"), result.getLong("updated_at"), result.getString("failure_summary"));
    }

    private Optional<PeriodState> periodState(Connection connection, String boardId, String periodId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT state,winners_snapshot,reward_plan FROM rp_leaderboard_reward_periods WHERE board_id=? AND period_id=?")) {
            statement.setString(1, boardId);
            statement.setString(2, periodId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(new PeriodState(rows.getString(1), rows.getString(2), rows.getString(3)))
                        : Optional.empty();
            }
        }
    }

    private void insertPeriod(Connection connection, FinalizationPeriod period) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO rp_leaderboard_reward_periods(board_id,period_id,metric,period_type,state,"
                        + "winners_snapshot,created_at,updated_at,finalized_at,announced_at,failure_summary,"
                        + "reward_package_count,verified_at,reward_plan) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, period.boardId());
            statement.setString(2, period.periodId());
            statement.setString(3, period.metric());
            statement.setString(4, period.periodType());
            statement.setString(5, "RESERVED");
            statement.setString(6, period.winnersSnapshot());
            statement.setLong(7, period.createdAt());
            statement.setLong(8, period.createdAt());
            statement.setLong(9, 0L);
            statement.setLong(10, 0L);
            statement.setString(11, "");
            statement.setInt(12, 0);
            statement.setLong(13, 0L);
            statement.setString(14, period.rewardPlan());
            statement.executeUpdate();
        }
    }

    private boolean completePeriod(Connection connection, FinalizationPeriod period, List<WinnerSnapshot> winners,
                                   List<RewardBundle> rewards) throws SQLException {
        markPeriodState(connection, period.boardId(), period.periodId(), "SNAPSHOT_CREATED", "");
        for (WinnerSnapshot winner : winners) insertWinner(connection, winner);
        markPeriodState(connection, period.boardId(), period.periodId(), "REWARDS_CREATING", "");
        return recoverPeriod(connection, period.boardId(), period.periodId(), period.rewardPlan());
    }

    private boolean recoverPeriod(Connection connection, String boardId, String periodId, String rewardPlan)
            throws SQLException {
        if (rewardPlan == null || rewardPlan.isBlank()) {
            int expected = countLedgerRows(connection, boardId, periodId);
            int missing = countMissingRewardPackages(connection, boardId, periodId);
            if (expected > 0 && missing == 0) {
                markPeriodFinalized(connection, boardId, periodId, expected);
                return true;
            }
            markPeriodRecoverable(connection, boardId, periodId,
                    "legacy period has no frozen reward plan; expected_rewards=" + expected
                            + " missing_packages=" + missing);
            return false;
        }
        final List<FrozenLeaderboardPlan.Entry> entries;
        try {
            entries = FrozenLeaderboardPlan.decode(rewardPlan);
        } catch (IllegalArgumentException error) {
            markPeriodRecoverable(connection, boardId, periodId,
                    "frozen reward plan unavailable or invalid: " + error.getMessage());
            return false;
        }
        long now = System.currentTimeMillis();
        for (FrozenLeaderboardPlan.Entry entry : entries) {
            if (!entry.validationFailure().isBlank()) {
                markPeriodRecoverable(connection, boardId, periodId, "reward=" + entry.rewardId()
                        + " player=" + entry.playerId() + " failure=" + entry.validationFailure());
                return false;
            }
            if (entry.components().isEmpty() && !entry.intentionallyEmpty()) {
                markPeriodRecoverable(connection, boardId, periodId,
                        "reward=" + entry.rewardId() + " player=" + entry.playerId()
                                + " produced an invalid empty package");
                return false;
            }
            WinnerSnapshot winner = new WinnerSnapshot(boardId, periodId, entry.playerId(), entry.playerName(),
                    entry.position(), entry.value(), entry.playerName().toLowerCase(java.util.Locale.ROOT)
                    + ':' + entry.playerId(), entry.rewardId(), now);
            insertWinner(connection, winner);
            RewardPackageRecord rewardPackage = new RewardPackageRecord(entry.packageId(), "leaderboard",
                    boardId + ':' + periodId, entry.playerId(),
                    site.mcrelicworld.relicprison.reward.RewardComponentState.PENDING, now, now,
                    entry.packagePayload(), "");
            List<RewardComponentRecord> components = entry.components().stream()
                    .map(draft -> draft.toRecord(entry.packageId(), "leaderboard", boardId + ':' + periodId,
                            entry.playerId(), now))
                    .toList();
            LeaderboardRewardRecord ledger = new LeaderboardRewardRecord(boardId, periodId, entry.playerId(),
                    entry.position(), entry.rewardId(), RewardDeliveryState.PACKAGE_CREATED, 1, now, now,
                    "reward package restored from frozen period plan");
            insertReward(connection, new RewardBundle(ledger, rewardPackage, components));
            insertPackageIfMissing(connection, rewardPackage);
            for (RewardComponentRecord component : components) {
                RewardLedgerRepository.insertComponentRecord(connection, component, database.storageType());
            }
            if (!packageExists(connection, entry.packageId())
                    || countPackageComponents(connection, entry.packageId()) != components.size()) {
                markPeriodRecoverable(connection, boardId, periodId,
                        "reward=" + entry.rewardId() + " player=" + entry.playerId()
                                + " package verification failed");
                return false;
            }
        }
        markPeriodState(connection, boardId, periodId, "REWARDS_VERIFIED", "");
        markPeriodFinalized(connection, boardId, periodId, entries.size());
        return true;
    }

    private static String freezeRewardBundles(List<WinnerSnapshot> winners, List<RewardBundle> rewards) {
        if (rewards.isEmpty()) return "";
        java.util.Map<UUID, WinnerSnapshot> winnerByPlayer = new java.util.HashMap<>();
        for (WinnerSnapshot winner : winners) winnerByPlayer.put(winner.playerId(), winner);
        List<FrozenLeaderboardPlan.Entry> entries = new ArrayList<>();
        for (RewardBundle reward : rewards) {
            WinnerSnapshot winner = winnerByPlayer.get(reward.ledger().playerId());
            String name = winner == null ? reward.ledger().playerId().toString() : winner.lastKnownName();
            BigDecimal value = winner == null ? BigDecimal.ZERO : winner.finalValue();
            List<site.mcrelicworld.relicprison.reward.RewardLedgerService.ComponentDraft> drafts =
                    reward.components().stream().map(component ->
                            new site.mcrelicworld.relicprison.reward.RewardLedgerService.ComponentDraft(
                                    component.componentId(), component.componentType(), component.payload(),
                                    component.amount(), component.dueAt(), component.componentId())).toList();
            boolean intentionallyEmpty = drafts.isEmpty()
                    && reward.rewardPackage().state()
                    != site.mcrelicworld.relicprison.reward.RewardComponentState.STAFF_REVIEW;
            entries.add(new FrozenLeaderboardPlan.Entry(reward.ledger().playerId(), name,
                    reward.ledger().finalPosition(), value, reward.ledger().rewardDefinitionId(),
                    reward.rewardPackage().packageId(), reward.rewardPackage().frozenPayload(),
                    intentionallyEmpty, reward.rewardPackage().failureSummary(), drafts));
        }
        return FrozenLeaderboardPlan.encode(entries);
    }

    private static void storeRewardPlan(Connection connection, String boardId, String periodId, String rewardPlan)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_leaderboard_reward_periods SET reward_plan=?,updated_at=? WHERE board_id=? "
                        + "AND period_id=? AND reward_plan=''")) {
            statement.setString(1, rewardPlan);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, boardId);
            statement.setString(4, periodId);
            statement.executeUpdate();
        }
    }

    private static int countPackageComponents(Connection connection, String packageId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM rp_reward_components WHERE package_id=?")) {
            statement.setString(1, packageId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    private void insertWinner(Connection connection, WinnerSnapshot winner) throws SQLException {
        boolean mysql = database.storageType() == StorageConfig.Type.MYSQL;
        String sql = mysql
                ? "INSERT IGNORE INTO rp_leaderboard_winner_snapshots(board_id,period_id,player_uuid,"
                + "last_known_name,final_position,final_value,tie_breaker,frozen_rewards,created_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?)"
                : "INSERT OR IGNORE INTO rp_leaderboard_winner_snapshots(board_id,period_id,player_uuid,"
                + "last_known_name,final_position,final_value,tie_breaker,frozen_rewards,created_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, winner.boardId());
            statement.setString(2, winner.periodId());
            statement.setString(3, winner.playerId().toString());
            statement.setString(4, winner.lastKnownName());
            statement.setInt(5, winner.finalPosition());
            statement.setString(6, winner.finalValue().toPlainString());
            statement.setString(7, winner.tieBreaker());
            statement.setString(8, winner.frozenRewards());
            statement.setLong(9, winner.createdAt());
            statement.executeUpdate();
        }
    }

    private void insertReward(Connection connection, LeaderboardRewardRecord record) throws SQLException {
        insertReward(connection, record, "");
    }

    private void insertReward(Connection connection, RewardBundle bundle) throws SQLException {
        insertReward(connection, bundle.ledger(), bundle.rewardPackage().packageId());
    }

    private void insertReward(Connection connection, LeaderboardRewardRecord record, String rewardPackageId)
            throws SQLException {
        boolean mysql = database.storageType() == StorageConfig.Type.MYSQL;
        String sql = mysql
                ? "INSERT IGNORE INTO rp_leaderboard_reward_ledger(board_id,period_id,player_uuid,final_position,"
                + "reward_definition_id,delivery_state,attempt_count,created_at,updated_at,failure_summary,"
                + "reward_package_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)"
                : "INSERT OR IGNORE INTO rp_leaderboard_reward_ledger(board_id,period_id,player_uuid,final_position,"
                + "reward_definition_id,delivery_state,attempt_count,created_at,updated_at,failure_summary,"
                + "reward_package_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.boardId());
            statement.setString(2, record.periodId());
            statement.setString(3, record.playerId().toString());
            statement.setInt(4, record.finalPosition());
            statement.setString(5, record.rewardDefinitionId());
            statement.setString(6, record.state().name());
            statement.setInt(7, record.attemptCount());
            statement.setLong(8, record.createdAt());
            statement.setLong(9, record.updatedAt());
            statement.setString(10, record.failureSummary());
            statement.setString(11, rewardPackageId == null ? "" : rewardPackageId);
            statement.executeUpdate();
        }
    }

    private static void insertPackageIfMissing(Connection connection, RewardPackageRecord rewardPackage)
            throws SQLException {
        if (packageExists(connection, rewardPackage.packageId())) return;
        RewardLedgerRepository.insertPackageRecord(connection, rewardPackage);
    }

    private static int countPackages(Connection connection, List<String> packageIds) throws SQLException {
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT package_id FROM rp_reward_packages WHERE package_id=?")) {
            for (String packageId : packageIds) {
                statement.setString(1, packageId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) count++;
                }
            }
        }
        return count;
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

    private static int countLedgerRows(Connection connection, String boardId, String periodId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM rp_leaderboard_reward_ledger WHERE board_id=? AND period_id=?")) {
            statement.setString(1, boardId);
            statement.setString(2, periodId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    private static int countMissingRewardPackages(Connection connection, String boardId, String periodId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT reward_package_id FROM rp_leaderboard_reward_ledger "
                        + "WHERE board_id=? AND period_id=?")) {
            statement.setString(1, boardId);
            statement.setString(2, periodId);
            int missing = 0;
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (!packageExists(connection, rows.getString(1))) missing++;
                }
            }
            return missing;
        }
    }

    private static List<IncompletePeriod> incompletePeriods(Connection connection, int limit) throws SQLException {
        List<IncompletePeriod> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT board_id,period_id,state,reward_package_count,failure_summary,reward_plan "
                        + "FROM rp_leaderboard_reward_periods WHERE state<>'FINALIZED' "
                        + "ORDER BY updated_at ASC LIMIT ?")) {
            statement.setInt(1, Math.max(1, Math.min(200, limit)));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    records.add(new IncompletePeriod(rows.getString("board_id"), rows.getString("period_id"),
                            rows.getString("state"), rows.getInt("reward_package_count"),
                            rows.getString("failure_summary"), rows.getString("reward_plan")));
                }
            }
        }
        return List.copyOf(records);
    }

    private static void markPeriodState(Connection connection, String boardId, String periodId, String state,
                                        String failure) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_leaderboard_reward_periods SET state=?,updated_at=?,failure_summary=? "
                        + "WHERE board_id=? AND period_id=?")) {
            statement.setString(1, state);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, failure == null ? "" : failure);
            statement.setString(4, boardId);
            statement.setString(5, periodId);
            statement.executeUpdate();
        }
    }

    private static void markPeriodRecoverable(Connection connection, String boardId, String periodId, String failure)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_leaderboard_reward_periods SET state='FAILED_RECOVERABLE',updated_at=?,"
                        + "failure_summary=?,recovery_attempts=recovery_attempts+1 WHERE board_id=? "
                        + "AND period_id=?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, failure == null ? "" : failure);
            statement.setString(3, boardId);
            statement.setString(4, periodId);
            statement.executeUpdate();
        }
    }

    private static void markPeriodFinalized(Connection connection, String boardId, String periodId,
                                            int packageCount) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_leaderboard_reward_periods SET state='FINALIZED',updated_at=?,finalized_at=?,"
                        + "verified_at=?,reward_package_count=?,failure_summary='' "
                        + "WHERE board_id=? AND period_id=?")) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setInt(4, packageCount);
            statement.setString(5, boardId);
            statement.setString(6, periodId);
            statement.executeUpdate();
        }
    }

    private static final String SELECT_LEDGER = "SELECT board_id,period_id,player_uuid,final_position,"
            + "reward_definition_id,delivery_state,attempt_count,created_at,updated_at,failure_summary "
            + "FROM rp_leaderboard_reward_ledger";

    public record FinalizationPeriod(String boardId, String periodId, String metric, String periodType,
                                     String winnersSnapshot, String rewardPlan, long createdAt) {
        public FinalizationPeriod(String boardId, String periodId, String metric, String periodType,
                                  String winnersSnapshot, long createdAt) {
            this(boardId, periodId, metric, periodType, winnersSnapshot, "", createdAt);
        }
    }
    public record WinnerSnapshot(String boardId, String periodId, UUID playerId, String lastKnownName,
                                 int finalPosition, BigDecimal finalValue, String tieBreaker,
                                 String frozenRewards, long createdAt) { }
    public record RewardBundle(LeaderboardRewardRecord ledger, RewardPackageRecord rewardPackage,
                               List<RewardComponentRecord> components) {
        public RewardBundle { components = List.copyOf(components); }
    }
    public record FinalizationResult(boolean created, boolean finalized, String state) { }
    public record IncompletePeriod(String boardId, String periodId, String state, int rewardPackageCount,
                                   String failureSummary, String rewardPlan) { }
    private record PeriodState(String state, String winnersSnapshot, String rewardPlan) { }
}
