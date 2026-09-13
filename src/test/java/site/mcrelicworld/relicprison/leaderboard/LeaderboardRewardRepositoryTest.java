package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.leaderboard.LeaderboardRewardRecord;
import site.mcrelicworld.relicprison.leaderboard.LeaderboardRewardRepository;
import site.mcrelicworld.relicprison.leaderboard.FrozenLeaderboardPlan;
import site.mcrelicworld.relicprison.leaderboard.RewardDeliveryState;
import site.mcrelicworld.relicprison.reward.RewardComponentRecord;
import site.mcrelicworld.relicprison.reward.RewardComponentState;
import site.mcrelicworld.relicprison.reward.RewardLedgerService;
import site.mcrelicworld.relicprison.reward.RewardPackageRecord;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LeaderboardRewardRepositoryTest {
    @TempDir Path temp;

    @Test
    void periodAndRewardReservationsAreDuplicateSafe() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            LeaderboardRewardRepository repository = new LeaderboardRewardRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            LeaderboardRewardRecord record = new LeaderboardRewardRecord("daily_blocks", "2026-07-31",
                    playerId, 1, "first", RewardDeliveryState.RESERVED, 0, now, now, "");

            assertTrue(repository.reservePeriod("daily_blocks", "2026-07-31", "blocks", "daily",
                    "winner").get(5, TimeUnit.SECONDS));
            assertFalse(repository.reservePeriod("daily_blocks", "2026-07-31", "blocks", "daily",
                    "winner").get(5, TimeUnit.SECONDS));
            assertTrue(repository.reserveReward(record).get(5, TimeUnit.SECONDS));
            assertFalse(repository.reserveReward(record).get(5, TimeUnit.SECONDS));

            assertEquals(1, repository.failedOrPending(10).get(5, TimeUnit.SECONDS).size());
            repository.mark("daily_blocks", "2026-07-31", playerId, "first", RewardDeliveryState.PACKAGE_CREATED,
                    1, "").get(5, TimeUnit.SECONDS);
            assertEquals(RewardDeliveryState.PACKAGE_CREATED, repository.record("daily_blocks", "2026-07-31",
                    playerId, "first").get(5, TimeUnit.SECONDS).orElseThrow().state());
        }
    }

    @Test
    void finalizationCreatesWinnerSnapshotLedgerAndPackageBeforeFinalizedState() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            LeaderboardRewardRepository repository = new LeaderboardRewardRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            String packageId = "lb-daily-2026-08-01-" + playerId + "-first";
            RewardPackageRecord rewardPackage = new RewardPackageRecord(packageId, "leaderboard",
                    "daily_blocks:2026-08-01", playerId, RewardComponentState.PENDING, now, now, "frozen", "");
            RewardComponentRecord component = RewardLedgerService.ComponentDraft.money("money", BigDecimal.TEN, now)
                    .toRecord(packageId, "leaderboard", "daily_blocks:2026-08-01", playerId, now);
            LeaderboardRewardRecord ledger = new LeaderboardRewardRecord("daily_blocks", "2026-08-01",
                    playerId, 1, "first", RewardDeliveryState.PACKAGE_CREATED, 1, now, now, "created");

            LeaderboardRewardRepository.FinalizationResult first = repository.finalizePeriod(
                    new LeaderboardRewardRepository.FinalizationPeriod("daily_blocks", "2026-08-01",
                            "blocks", "daily", "1," + playerId + ",Player,100", now),
                    List.of(new LeaderboardRewardRepository.WinnerSnapshot("daily_blocks", "2026-08-01",
                            playerId, "Player", 1, new BigDecimal("100"), "player:" + playerId,
                            "first", now)),
                    List.of(new LeaderboardRewardRepository.RewardBundle(ledger, rewardPackage, List.of(component))))
                    .get(5, TimeUnit.SECONDS);
            LeaderboardRewardRepository.FinalizationResult second = repository.finalizePeriod(
                    new LeaderboardRewardRepository.FinalizationPeriod("daily_blocks", "2026-08-01",
                            "blocks", "daily", "different", now),
                    List.of(), List.of()).get(5, TimeUnit.SECONDS);

            assertTrue(first.created());
            assertTrue(first.finalized());
            assertFalse(second.created());
            assertTrue(second.finalized());
            assertEquals(RewardDeliveryState.PACKAGE_CREATED, repository.record("daily_blocks", "2026-08-01",
                    playerId, "first").get(5, TimeUnit.SECONDS).orElseThrow().state());
            assertEquals(1, database.submitIdempotent(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery(
                             "SELECT COUNT(*) FROM rp_reward_packages WHERE package_id='" + packageId + "'")) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void existingReservedPeriodResumesInsteadOfStrandingRewards() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            LeaderboardRewardRepository repository = new LeaderboardRewardRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            String snapshot = "1," + playerId + ",Player,100";
            String packageId = "lb-daily_blocks-2026-08-02-" + playerId + "-first";
            assertTrue(repository.reservePeriod("daily_blocks", "2026-08-02", "blocks", "daily",
                    snapshot).get(5, TimeUnit.SECONDS));

            RewardPackageRecord rewardPackage = new RewardPackageRecord(packageId, "leaderboard",
                    "daily_blocks:2026-08-02", playerId, RewardComponentState.PENDING, now, now, "frozen", "");
            RewardComponentRecord component = RewardLedgerService.ComponentDraft.money("money", BigDecimal.TEN, now)
                    .toRecord(packageId, "leaderboard", "daily_blocks:2026-08-02", playerId, now);
            LeaderboardRewardRecord ledger = new LeaderboardRewardRecord("daily_blocks", "2026-08-02",
                    playerId, 1, "first", RewardDeliveryState.PACKAGE_CREATED, 1, now, now, "created");

            LeaderboardRewardRepository.FinalizationResult result = repository.finalizePeriod(
                    new LeaderboardRewardRepository.FinalizationPeriod("daily_blocks", "2026-08-02",
                            "blocks", "daily", snapshot, now),
                    List.of(new LeaderboardRewardRepository.WinnerSnapshot("daily_blocks", "2026-08-02",
                            playerId, "Player", 1, new BigDecimal("100"), "player:" + playerId,
                            "first", now)),
                    List.of(new LeaderboardRewardRepository.RewardBundle(ledger, rewardPackage, List.of(component))))
                    .get(5, TimeUnit.SECONDS);

            assertFalse(result.created());
            assertTrue(result.finalized());
            assertEquals("recovered-finalized", result.state());
            assertEquals(1, database.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM rp_reward_packages WHERE package_id=?")) {
                    statement.setString(1, packageId);
                    try (var rows = statement.executeQuery()) {
                        return rows.next() ? rows.getInt(1) : 0;
                    }
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentFinalizersAcrossManagersCreateOnePeriodAndPackage() throws Exception {
        Path databaseFile = temp.resolve("shared-leaderboards.db");
        try (DatabaseManager firstDatabase = database(databaseFile);
             DatabaseManager secondDatabase = database(databaseFile)) {
            firstDatabase.initializeAsync().get(10, TimeUnit.SECONDS);
            secondDatabase.initializeAsync().get(10, TimeUnit.SECONDS);
            LeaderboardRewardRepository firstRepository = new LeaderboardRewardRepository(firstDatabase);
            LeaderboardRewardRepository secondRepository = new LeaderboardRewardRepository(secondDatabase);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            String snapshot = "1," + playerId + ",Player,100";
            String packageId = "lb-daily_blocks-2026-08-05-" + playerId + "-first";
            LeaderboardRewardRepository.FinalizationPeriod period =
                    new LeaderboardRewardRepository.FinalizationPeriod("daily_blocks", "2026-08-05",
                            "blocks", "daily", snapshot, now);
            LeaderboardRewardRepository.WinnerSnapshot winner = new LeaderboardRewardRepository.WinnerSnapshot(
                    "daily_blocks", "2026-08-05", playerId, "Player", 1, new BigDecimal("100"),
                    "player:" + playerId, "first", now);
            RewardPackageRecord rewardPackage = new RewardPackageRecord(packageId, "leaderboard",
                    "daily_blocks:2026-08-05", playerId, RewardComponentState.PENDING, now, now, "frozen", "");
            RewardComponentRecord component = RewardLedgerService.ComponentDraft.money("money", BigDecimal.TEN, now)
                    .toRecord(packageId, "leaderboard", "daily_blocks:2026-08-05", playerId, now);
            LeaderboardRewardRecord ledger = new LeaderboardRewardRecord("daily_blocks", "2026-08-05",
                    playerId, 1, "first", RewardDeliveryState.PACKAGE_CREATED, 1, now, now, "created");
            List<LeaderboardRewardRepository.WinnerSnapshot> winners = List.of(winner);
            List<LeaderboardRewardRepository.RewardBundle> rewards =
                    List.of(new LeaderboardRewardRepository.RewardBundle(ledger, rewardPackage, List.of(component)));

            CompletableFuture<LeaderboardRewardRepository.FinalizationResult> first =
                    firstRepository.finalizePeriod(period, winners, rewards);
            CompletableFuture<LeaderboardRewardRepository.FinalizationResult> second =
                    secondRepository.finalizePeriod(period, winners, rewards);
            CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

            int createdPeriods = (first.get().created() ? 1 : 0) + (second.get().created() ? 1 : 0);
            assertEquals(1, createdPeriods);
            assertTrue(first.get().finalized());
            assertTrue(second.get().finalized());
            assertEquals(1, firstDatabase.submitIdempotent(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery(
                             "SELECT COUNT(*) FROM rp_leaderboard_reward_periods "
                                     + "WHERE board_id='daily_blocks' AND period_id='2026-08-05'")) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }).get(5, TimeUnit.SECONDS));
            assertEquals(1, firstDatabase.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM rp_reward_packages WHERE package_id=?")) {
                    statement.setString(1, packageId);
                    try (var result = statement.executeQuery()) {
                        return result.next() ? result.getInt(1) : 0;
                    }
                }
            }).get(5, TimeUnit.SECONDS));
            assertEquals(1, firstDatabase.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM rp_reward_components WHERE package_id=?")) {
                    statement.setString(1, packageId);
                    try (var result = statement.executeQuery()) {
                        return result.next() ? result.getInt(1) : 0;
                    }
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void recoveryFinalizesIncompletePeriodOnlyWhenPackagesExist() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            LeaderboardRewardRepository repository = new LeaderboardRewardRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            String packageId = "lb-daily_blocks-2026-08-03-" + playerId + "-first";
            database.submitIdempotent(connection -> {
                try (var period = connection.prepareStatement(
                        "INSERT INTO rp_leaderboard_reward_periods(board_id,period_id,metric,period_type,state,"
                                + "winners_snapshot,created_at,updated_at,finalized_at,announced_at,"
                                + "failure_summary,reward_package_count,verified_at) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    period.setString(1, "daily_blocks");
                    period.setString(2, "2026-08-03");
                    period.setString(3, "blocks");
                    period.setString(4, "daily");
                    period.setString(5, "REWARDS_CREATING");
                    period.setString(6, "snapshot");
                    period.setLong(7, now);
                    period.setLong(8, now);
                    period.setLong(9, 0L);
                    period.setLong(10, 0L);
                    period.setString(11, "");
                    period.setInt(12, 0);
                    period.setLong(13, 0L);
                    period.executeUpdate();
                }
                try (var ledger = connection.prepareStatement(
                        "INSERT INTO rp_leaderboard_reward_ledger(board_id,period_id,player_uuid,final_position,"
                                + "reward_definition_id,delivery_state,attempt_count,created_at,updated_at,"
                                + "failure_summary,reward_package_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
                    ledger.setString(1, "daily_blocks");
                    ledger.setString(2, "2026-08-03");
                    ledger.setString(3, playerId.toString());
                    ledger.setInt(4, 1);
                    ledger.setString(5, "first");
                    ledger.setString(6, RewardDeliveryState.PACKAGE_CREATED.name());
                    ledger.setInt(7, 1);
                    ledger.setLong(8, now);
                    ledger.setLong(9, now);
                    ledger.setString(10, "");
                    ledger.setString(11, packageId);
                    ledger.executeUpdate();
                }
                try (var reward = connection.prepareStatement(
                        "INSERT INTO rp_reward_packages(package_id,source_system,source_operation_id,player_uuid,"
                                + "state,created_at,updated_at,frozen_payload,failure_summary) "
                                + "VALUES(?,?,?,?,?,?,?,?,?)")) {
                    reward.setString(1, packageId);
                    reward.setString(2, "leaderboard");
                    reward.setString(3, "daily_blocks:2026-08-03");
                    reward.setString(4, playerId.toString());
                    reward.setString(5, RewardComponentState.PENDING.name());
                    reward.setLong(6, now);
                    reward.setLong(7, now);
                    reward.setString(8, "frozen");
                    reward.setString(9, "");
                    reward.executeUpdate();
                }
                return null;
            }).get(5, TimeUnit.SECONDS);

            assertEquals(1, repository.recoverIncompletePeriods(10).get(5, TimeUnit.SECONDS));
            assertEquals(0, repository.recoverIncompletePeriods(10).get(5, TimeUnit.SECONDS));
            assertTrue(repository.incompletePeriods(10).get(5, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void recoveryMarksLegacyPeriodWithoutPackagesRecoverable() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            LeaderboardRewardRepository repository = new LeaderboardRewardRepository(database);
            long now = System.currentTimeMillis();
            assertTrue(repository.reservePeriod("daily_blocks", "2026-08-04", "blocks", "daily",
                    "snapshot").get(5, TimeUnit.SECONDS));

            assertEquals(1, repository.recoverIncompletePeriods(10).get(5, TimeUnit.SECONDS));
            assertEquals("FAILED_RECOVERABLE", database.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT state FROM rp_leaderboard_reward_periods WHERE board_id='daily_blocks' "
                                + "AND period_id='2026-08-04'");
                     var rows = statement.executeQuery()) {
                    return rows.next() ? rows.getString(1) : "";
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void recoveryCreatesMissingPackagesFromFrozenPlanOnly() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            LeaderboardRewardRepository repository = new LeaderboardRewardRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            String packageId = "lb-frozen-recovery";
            String plan = FrozenLeaderboardPlan.encode(List.of(new FrozenLeaderboardPlan.Entry(playerId,
                    "OfflineWinner", 1, new BigDecimal("500"), "original-tier", packageId,
                    "original-frozen-payload", false, List.of(
                    RewardLedgerService.ComponentDraft.money("money", new BigDecimal("100"), now),
                    RewardLedgerService.ComponentDraft.consoleCommand("command",
                            "give OfflineWinner diamond 1", now)))));
            assertTrue(repository.reservePeriod("daily_blocks", "2026-08-05", "blocks", "daily",
                    "1," + playerId + ",OfflineWinner,500").get(5, TimeUnit.SECONDS));
            database.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "UPDATE rp_leaderboard_reward_periods SET reward_plan=? WHERE board_id=? AND period_id=?")) {
                    statement.setString(1, plan);
                    statement.setString(2, "daily_blocks");
                    statement.setString(3, "2026-08-05");
                    statement.executeUpdate();
                }
                return null;
            }).get(5, TimeUnit.SECONDS);

            assertEquals(1, repository.recoverIncompletePeriods(10).get(5, TimeUnit.SECONDS));
            assertEquals(0, repository.recoverIncompletePeriods(10).get(5, TimeUnit.SECONDS));
            assertEquals(2, database.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM rp_reward_components WHERE package_id=?")) {
                    statement.setString(1, packageId);
                    try (var rows = statement.executeQuery()) {
                        return rows.next() ? rows.getInt(1) : 0;
                    }
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    private DatabaseManager database() {
        return database(temp.resolve("data.db"));
    }

    private DatabaseManager database(Path file) {
        return new DatabaseManager(Logger.getLogger("test"), new StorageConfig(StorageConfig.Type.SQLITE,
                file, "localhost", 3306, "db", "user", "", "useSSL=false",
                1, 100, 3, 10, 15, 20, 10));
    }
}
