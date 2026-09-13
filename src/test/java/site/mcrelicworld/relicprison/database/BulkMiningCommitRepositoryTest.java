package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.mining.BulkBlockSnapshot;
import site.mcrelicworld.relicprison.mining.BulkMiningCommitRepository;
import site.mcrelicworld.relicprison.mining.BulkMiningCommittedResult;
import site.mcrelicworld.relicprison.mining.BulkMiningTransactionRecord;
import site.mcrelicworld.relicprison.mining.BulkMiningTransactionRepository;
import site.mcrelicworld.relicprison.mining.BulkMiningTransactionState;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BulkMiningCommitRepositoryTest {
    @TempDir Path temp;

    @Test
    void crashReplayAppliesStatisticsSellAndProgressionOnce() throws Exception {
        UUID playerId = UUID.randomUUID();
        BulkMiningCommittedResult result = result();
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            insertProfile(database, playerId);
            insertTransaction(database, playerId, result);
            BulkMiningCommitRepository commits = new BulkMiningCommitRepository(database);

            assertTrue(commits.apply("tx-commit", playerId, result).get(5, TimeUnit.SECONDS));
            assertFalse(commits.apply("tx-commit", playerId, result).get(5, TimeUnit.SECONDS));
            assertCommittedOnce(database, playerId);
        }

        try (DatabaseManager reconnected = database()) {
            reconnected.initializeAsync().get(10, TimeUnit.SECONDS);
            BulkMiningCommitRepository commits = new BulkMiningCommitRepository(reconnected);
            assertFalse(commits.apply("tx-commit", playerId, result).get(5, TimeUnit.SECONDS));
            assertCommittedOnce(reconnected, playerId);
        }
    }

    @Test
    void crashAfterProgressionRollsBackAndRecoveryAppliesEverythingOnce() throws Exception {
        assertCrashRollbackAndReplay(true);
    }

    @Test
    void crashAfterStatisticsRollsBackAndRecoveryAppliesEverythingOnce() throws Exception {
        assertCrashRollbackAndReplay(false);
    }

    private void assertCrashRollbackAndReplay(boolean afterProgression) throws Exception {
        UUID playerId = UUID.randomUUID();
        BulkMiningCommittedResult result = result();
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            insertProfile(database, playerId);
            insertTransaction(database, playerId, result);
            BulkMiningCommitRepository.CommitObserver crash = new BulkMiningCommitRepository.CommitObserver() {
                @Override public void afterProgression() {
                    if (afterProgression) throw new SimulatedCrash();
                }

                @Override public void afterStatistics() {
                    if (!afterProgression) throw new SimulatedCrash();
                }
            };
            BulkMiningCommitRepository interrupted = new BulkMiningCommitRepository(database, crash);

            assertThrows(Exception.class,
                    () -> interrupted.apply("tx-commit", playerId, result).get(5, TimeUnit.SECONDS));
            assertUncommitted(database, playerId);

            BulkMiningCommitRepository recovered = new BulkMiningCommitRepository(database);
            assertTrue(recovered.apply("tx-commit", playerId, result).get(5, TimeUnit.SECONDS));
            assertFalse(recovered.apply("tx-commit", playerId, result).get(5, TimeUnit.SECONDS));
            assertCommittedOnce(database, playerId);
        }
    }

    private BulkMiningCommittedResult result() {
        return new BulkMiningCommittedResult(1_750_000_000_000L, 2, 5, new BigDecimal("12.50"), 17, true,
                List.of(new BulkMiningCommittedResult.Period("daily", "2026-09-10"),
                        new BulkMiningCommittedResult.Period("lifetime", "lifetime")),
                List.of(new BulkMiningCommittedResult.MineResult("mine-a", Map.of("STONE", 2),
                        Map.of("itemsadder:test", 1), 1, 0, 1, 3)));
    }

    private void insertProfile(DatabaseManager database, UUID playerId) throws Exception {
        database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rp_player_profiles(uuid,last_name,current_rank,current_prestige,first_join,last_join,"
                            + "autosell,autopickup,autosmelt,autoblock,lifetime_blocks,daily_blocks,weekly_blocks,"
                            + "monthly_blocks,daily_period,weekly_period,monthly_period,money_earned,data_version,"
                            + "updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                int index = 1;
                statement.setString(index++, playerId.toString());
                statement.setString(index++, "Miner");
                statement.setString(index++, "a");
                statement.setString(index++, null);
                statement.setLong(index++, 1L);
                statement.setLong(index++, 1L);
                statement.setBoolean(index++, true);
                statement.setBoolean(index++, true);
                statement.setBoolean(index++, true);
                statement.setBoolean(index++, true);
                statement.setLong(index++, 10L);
                statement.setLong(index++, 4L);
                statement.setLong(index++, 4L);
                statement.setLong(index++, 4L);
                statement.setString(index++, "2026-09-10");
                statement.setString(index++, "2026-W37");
                statement.setString(index++, "2026-09");
                statement.setString(index++, "2.50");
                statement.setInt(index++, 1);
                statement.setLong(index, 1L);
                statement.executeUpdate();
            }
            return null;
        }).get(5, TimeUnit.SECONDS);
    }

    private void insertTransaction(DatabaseManager database, UUID playerId,
                                   BulkMiningCommittedResult result) throws Exception {
        BulkMiningTransactionRepository transactions = new BulkMiningTransactionRepository(database);
        long now = System.currentTimeMillis();
        BulkMiningTransactionRecord record = new BulkMiningTransactionRecord("tx-commit", playerId, "TEST",
                "mine-a", BulkMiningTransactionState.REWARD_DELIVERED, 2, 2, "pkg-effects",
                List.of(new BulkBlockSnapshot(UUID.randomUUID(), 1, 2, 3, "STONE", "minecraft:stone", "")),
                "", result.encode(), now, now, 0, "");
        assertTrue(transactions.create(record).get(5, TimeUnit.SECONDS));
    }

    private void assertCommittedOnce(DatabaseManager database, UUID playerId) throws Exception {
        database.submitIdempotent(connection -> {
            try (PreparedStatement profile = connection.prepareStatement(
                    "SELECT lifetime_blocks,daily_blocks,money_earned FROM rp_player_profiles WHERE uuid=?")) {
                profile.setString(1, playerId.toString());
                try (ResultSet row = profile.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(12L, row.getLong("lifetime_blocks"));
                    assertEquals(6L, row.getLong("daily_blocks"));
                    assertEquals(new BigDecimal("15.00"), new BigDecimal(row.getString("money_earned")));
                }
            }
            assertEquals(5L, scalar(connection, "SELECT items_sold FROM rp_player_statistics WHERE player_uuid=?",
                    playerId.toString()));
            assertEquals(2L, dimension(connection, playerId, "lifetime", "lifetime", "total", "blocks"));
            assertEquals(1L, dimension(connection, playerId, "lifetime", "lifetime", "flag", "autosell"));
            assertEquals(0L, dimension(connection, playerId, "lifetime", "lifetime", "flag", "autopickup"));
            assertEquals(3L, dimension(connection, playerId, "lifetime", "lifetime", "delivery",
                    "fallback_dropped_items"));
            assertEquals(1L, scalar(connection,
                    "SELECT COUNT(*) FROM rp_bulk_mining_commits WHERE transaction_id=?", "tx-commit"));
            assertEquals("FINALIZING", text(connection,
                    "SELECT state FROM rp_bulk_mining_transactions WHERE transaction_id=?", "tx-commit"));
            return null;
        }).get(5, TimeUnit.SECONDS);
    }

    private void assertUncommitted(DatabaseManager database, UUID playerId) throws Exception {
        database.submitIdempotent(connection -> {
            try (PreparedStatement profile = connection.prepareStatement(
                    "SELECT lifetime_blocks,daily_blocks,money_earned FROM rp_player_profiles WHERE uuid=?")) {
                profile.setString(1, playerId.toString());
                try (ResultSet row = profile.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(10L, row.getLong("lifetime_blocks"));
                    assertEquals(4L, row.getLong("daily_blocks"));
                    assertEquals(new BigDecimal("2.50"), new BigDecimal(row.getString("money_earned")));
                }
            }
            assertEquals(0L, scalar(connection,
                    "SELECT COUNT(*) FROM rp_bulk_mining_commits WHERE transaction_id=?", "tx-commit"));
            assertEquals(0L, scalar(connection,
                    "SELECT COUNT(*) FROM rp_player_statistics WHERE player_uuid=?", playerId.toString()));
            assertEquals("REWARD_DELIVERED", text(connection,
                    "SELECT state FROM rp_bulk_mining_transactions WHERE transaction_id=?", "tx-commit"));
            return null;
        }).get(5, TimeUnit.SECONDS);
    }

    private static long dimension(java.sql.Connection connection, UUID playerId, String periodType,
                                  String periodKey, String dimensionType, String dimensionKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT blocks FROM rp_mining_statistics WHERE player_uuid=? AND period_type=? AND period_key=?"
                        + " AND dimension_type=? AND dimension_key=?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, periodType);
            statement.setString(3, periodKey);
            statement.setString(4, dimensionType);
            statement.setString(5, dimensionKey);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        }
    }

    private static long scalar(java.sql.Connection connection, String sql, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        }
    }

    private static String text(java.sql.Connection connection, String sql, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getString(1) : "";
            }
        }
    }

    private DatabaseManager database() {
        return new DatabaseManager(Logger.getLogger("test"), new StorageConfig(StorageConfig.Type.SQLITE,
                temp.resolve("bulk-commit.db"), "localhost", 3306, "db", "user", "", "useSSL=false",
                1, 100, 0, 1, 15, 20, 10));
    }

    private static final class SimulatedCrash extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
