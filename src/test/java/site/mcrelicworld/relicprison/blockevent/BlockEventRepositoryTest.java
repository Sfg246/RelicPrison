package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.blockevent.BlockEventRepository;
import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.reward.RewardComponentRecord;
import site.mcrelicworld.relicprison.reward.RewardComponentState;
import site.mcrelicworld.relicprison.reward.FrozenRewardPlanCodec;
import site.mcrelicworld.relicprison.reward.RewardLedgerService;
import site.mcrelicworld.relicprison.reward.RewardPackageRecord;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockEventRepositoryTest {
    @TempDir Path temp;

    @Test
    void triggerReservationIsUniqueAndCanStoreRewardPackage() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            BlockEventRepository repository = new BlockEventRepository(database);
            UUID playerId = UUID.randomUUID();
            BlockEventRepository.TriggerRecord trigger = new BlockEventRepository.TriggerRecord("trigger-1",
                    "daily-target", playerId, "operation-1", "bulk-action", "daily_target",
                    "2026-08-01", 0L, "be-claim-daily-target");

            assertTrue(repository.reserveTrigger(trigger).get(5, TimeUnit.SECONDS));
            assertFalse(repository.reserveTrigger(trigger).get(5, TimeUnit.SECONDS));
            repository.markTriggerComplete("trigger-1", "package-1").get(5, TimeUnit.SECONDS);

            assertEquals("package-1", database.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT package_id FROM rp_block_event_triggers WHERE trigger_id=?")) {
                    statement.setString(1, "trigger-1");
                    try (var result = statement.executeQuery()) {
                        return result.next() ? result.getString(1) : "";
                    }
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void triggerStateAndRewardPackageCommitAtomicallyAndRepeatedRecoveryDoesNotDuplicate() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            BlockEventRepository repository = new BlockEventRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            BlockEventRepository.TriggerRecord trigger = new BlockEventRepository.TriggerRecord("trigger-atomic",
                    "first-time", playerId, "operation-2", "normal-action", "first_time",
                    "", 0L, "be-claim-first-time");
            RewardPackageRecord rewardPackage = new RewardPackageRecord("trigger-atomic-pkg", "block-event",
                    "trigger-atomic", playerId, RewardComponentState.PENDING, now, now, "frozen-event", "");
            RewardComponentRecord component = RewardLedgerService.ComponentDraft.money("money", BigDecimal.TEN, now)
                    .toRecord("trigger-atomic-pkg", "block-event", "trigger-atomic", playerId, now);
            BlockEventRepository.StateKey stateKey = new BlockEventRepository.StateKey(playerId, "first-time",
                    "first:normal-action");

            BlockEventRepository.TriggerCommitResult first = repository.reserveStateAndPackage(trigger,
                    Map.of(stateKey, new BlockEventRepository.State(now, now, 1L)), rewardPackage,
                    List.of(component), "frozen-event").get(5, TimeUnit.SECONDS);
            BlockEventRepository.TriggerCommitResult second = repository.reserveStateAndPackage(trigger,
                    Map.of(stateKey, new BlockEventRepository.State(now, now, 1L)), rewardPackage,
                    List.of(component), "frozen-event").get(5, TimeUnit.SECONDS);

            assertTrue(first.createdTrigger());
            assertFalse(second.createdTrigger());
            assertTrue(second.packageExists());
            assertEquals(1, database.submitIdempotent(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery(
                             "SELECT COUNT(*) FROM rp_reward_packages WHERE package_id='trigger-atomic-pkg'")) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }).get(5, TimeUnit.SECONDS));
            assertEquals(0, repository.recoverIncompleteTriggers().get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void logicalClaimPreventsConcurrentFirstTimePackages() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            BlockEventRepository repository = new BlockEventRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            String logicalClaim = "be-claim-" + UUID.randomUUID();
            BlockEventRepository.TriggerRecord first = new BlockEventRepository.TriggerRecord("trigger-first-a",
                    "first-time", playerId, "operation-a", "normal-action", "first_time",
                    "", 0L, logicalClaim);
            BlockEventRepository.TriggerRecord second = new BlockEventRepository.TriggerRecord("trigger-first-b",
                    "first-time", playerId, "operation-b", "normal-action", "first_time",
                    "", 0L, logicalClaim);
            RewardPackageRecord rewardPackage = new RewardPackageRecord("first-time-logical-pkg", "block-event",
                    first.triggerId(), playerId, RewardComponentState.PENDING, now, now, "frozen-event", "");
            RewardComponentRecord component = RewardLedgerService.ComponentDraft.money("money", BigDecimal.TEN, now)
                    .toRecord("first-time-logical-pkg", "block-event", first.triggerId(), playerId, now);
            BlockEventRepository.StateKey stateKey = new BlockEventRepository.StateKey(playerId, "first-time",
                    "first");

            BlockEventRepository.TriggerCommitResult created = repository.reserveStateAndPackage(first,
                    Map.of(stateKey, new BlockEventRepository.State(now, now, 1L)), rewardPackage,
                    List.of(component), "frozen-event").get(5, TimeUnit.SECONDS);
            BlockEventRepository.TriggerCommitResult duplicate = repository.reserveStateAndPackage(second,
                    Map.of(stateKey, new BlockEventRepository.State(now, now, 1L)), rewardPackage,
                    List.of(component), "frozen-event").get(5, TimeUnit.SECONDS);

            assertTrue(created.createdTrigger());
            assertFalse(duplicate.createdTrigger());
            assertTrue(duplicate.packageExists());
            assertEquals(1, database.submitIdempotent(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery(
                             "SELECT COUNT(*) FROM rp_reward_packages WHERE package_id='first-time-logical-pkg'")) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }).get(5, TimeUnit.SECONDS));
            assertEquals(1, database.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT duplicate_count FROM rp_block_event_triggers WHERE logical_claim_key=?")) {
                    statement.setString(1, logicalClaim);
                    try (var result = statement.executeQuery()) {
                        return result.next() ? result.getInt(1) : 0;
                    }
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void logicalClaimPreventsConcurrentClaimsAcrossManagers() throws Exception {
        Path databaseFile = temp.resolve("shared-block-events.db");
        try (DatabaseManager firstDatabase = database(databaseFile);
             DatabaseManager secondDatabase = database(databaseFile)) {
            firstDatabase.initializeAsync().get(10, TimeUnit.SECONDS);
            secondDatabase.initializeAsync().get(10, TimeUnit.SECONDS);
            BlockEventRepository firstRepository = new BlockEventRepository(firstDatabase);
            BlockEventRepository secondRepository = new BlockEventRepository(secondDatabase);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            String logicalClaim = "be-claim-" + UUID.randomUUID();
            String packageId = "first-time-cross-manager-pkg";
            BlockEventRepository.TriggerRecord first = new BlockEventRepository.TriggerRecord("trigger-cross-a",
                    "first-time", playerId, "operation-a", "normal-action", "first_time",
                    "", 0L, logicalClaim);
            BlockEventRepository.TriggerRecord second = new BlockEventRepository.TriggerRecord("trigger-cross-b",
                    "first-time", playerId, "operation-b", "normal-action", "first_time",
                    "", 0L, logicalClaim);
            RewardPackageRecord rewardPackage = new RewardPackageRecord(packageId, "block-event",
                    first.triggerId(), playerId, RewardComponentState.PENDING, now, now, "frozen-event", "");
            RewardComponentRecord component = RewardLedgerService.ComponentDraft.money("money", BigDecimal.TEN, now)
                    .toRecord(packageId, "block-event", first.triggerId(), playerId, now);
            BlockEventRepository.StateKey stateKey = new BlockEventRepository.StateKey(playerId, "first-time",
                    "first");

            CompletableFuture<BlockEventRepository.TriggerCommitResult> firstClaim =
                    firstRepository.reserveStateAndPackage(first,
                            Map.of(stateKey, new BlockEventRepository.State(now, now, 1L)), rewardPackage,
                            List.of(component), "frozen-event");
            CompletableFuture<BlockEventRepository.TriggerCommitResult> secondClaim =
                    secondRepository.reserveStateAndPackage(second,
                            Map.of(stateKey, new BlockEventRepository.State(now, now, 1L)), rewardPackage,
                            List.of(component), "frozen-event");
            CompletableFuture.allOf(firstClaim, secondClaim).get(10, TimeUnit.SECONDS);

            int createdClaims = (firstClaim.get().createdTrigger() ? 1 : 0)
                    + (secondClaim.get().createdTrigger() ? 1 : 0);
            assertEquals(1, createdClaims);
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
                        "SELECT duplicate_count FROM rp_block_event_triggers WHERE logical_claim_key=?")) {
                    statement.setString(1, logicalClaim);
                    try (var result = statement.executeQuery()) {
                        return result.next() ? result.getInt(1) : 0;
                    }
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void incompleteClaimWithExistingPackageCommitsDuringRecovery() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            BlockEventRepository repository = new BlockEventRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            database.submitIdempotent(connection -> {
                try (var trigger = connection.prepareStatement(
                        "INSERT INTO rp_block_event_triggers(trigger_id,event_id,player_uuid,operation_id,"
                                + "block_identity,trigger_type,period_id,milestone,state,package_id,created_at,"
                                + "updated_at,failure_summary,frozen_payload,logical_claim_key) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    trigger.setString(1, "recover-existing");
                    trigger.setString(2, "daily-target");
                    trigger.setString(3, playerId.toString());
                    trigger.setString(4, "operation");
                    trigger.setString(5, "normal-action");
                    trigger.setString(6, "daily_target");
                    trigger.setString(7, "2026-08-01");
                    trigger.setLong(8, 0L);
                    trigger.setString(9, "REWARD_CREATED");
                    trigger.setString(10, "recover-existing-pkg");
                    trigger.setLong(11, now);
                    trigger.setLong(12, now);
                    trigger.setString(13, "");
                    trigger.setString(14, "event=daily-target;blocks=1");
                    trigger.setString(15, "be-claim-recover-existing");
                    trigger.executeUpdate();
                }
                try (var reward = connection.prepareStatement(
                        "INSERT INTO rp_reward_packages(package_id,source_system,source_operation_id,player_uuid,"
                                + "state,created_at,updated_at,frozen_payload,failure_summary) "
                                + "VALUES(?,?,?,?,?,?,?,?,?)")) {
                    reward.setString(1, "recover-existing-pkg");
                    reward.setString(2, "block-event");
                    reward.setString(3, "recover-existing");
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

            assertEquals(1, repository.recoverIncompleteTriggers().get(5, TimeUnit.SECONDS));
            assertEquals(0, repository.recoverIncompleteTriggers().get(5, TimeUnit.SECONDS));
            assertEquals("COMMITTED", database.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT state FROM rp_block_event_triggers WHERE trigger_id='recover-existing'");
                     var result = statement.executeQuery()) {
                    return result.next() ? result.getString(1) : "";
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void frozenClaimRecoversOriginalRewardAfterConfigurationChanges() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            BlockEventRepository repository = new BlockEventRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            BlockEventRepository.TriggerRecord trigger = new BlockEventRepository.TriggerRecord("frozen-crash",
                    "deleted-event", playerId, "operation", "normal-action", "first_time", "", 0,
                    "claim-frozen-crash");
            List<RewardLedgerService.ComponentDraft> original = List.of(
                    RewardLedgerService.ComponentDraft.money("money", new BigDecimal("25"), now),
                    RewardLedgerService.ComponentDraft.consoleCommand("command", "give Original diamond 1", now));
            String frozen = FrozenRewardPlanCodec.encode(original);

            assertTrue(repository.reserveFrozenTrigger(trigger, Map.of(), frozen).get(5, TimeUnit.SECONDS));
            BlockEventRepository.IncompleteTrigger incomplete = repository.incompleteTriggers(10)
                    .get(5, TimeUnit.SECONDS).getFirst();
            String packageId = "frozen-crash-pkg";
            List<RewardComponentRecord> components = FrozenRewardPlanCodec.decode(incomplete.frozenPayload()).stream()
                    .map(draft -> draft.toRecord(packageId, "block-event", trigger.triggerId(), playerId, now))
                    .toList();
            RewardPackageRecord rewardPackage = new RewardPackageRecord(packageId, "block-event",
                    trigger.triggerId(), playerId, RewardComponentState.PENDING, now, now, frozen, "");

            assertTrue(repository.createPackageForExistingTrigger(incomplete, rewardPackage, components, frozen)
                    .get(5, TimeUnit.SECONDS));
            assertTrue(repository.createPackageForExistingTrigger(incomplete, rewardPackage, components, frozen)
                    .get(5, TimeUnit.SECONDS));
            assertEquals(2, database.submitIdempotent(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM rp_reward_components WHERE package_id=?")) {
                    statement.setString(1, packageId);
                    try (var rows = statement.executeQuery()) {
                        return rows.next() ? rows.getInt(1) : 0;
                    }
                }
            }).get(5, TimeUnit.SECONDS));
            assertEquals(0, repository.incompleteTriggers(10).get(5, TimeUnit.SECONDS).size());
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
