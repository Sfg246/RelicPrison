package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.mining.BulkBlockSnapshot;
import site.mcrelicworld.relicprison.mining.BulkMiningTransactionRecord;
import site.mcrelicworld.relicprison.mining.BulkMiningTransactionRepository;
import site.mcrelicworld.relicprison.mining.BulkMiningTransactionState;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BulkMiningTransactionRepositoryTest {
    @TempDir Path temp;

    @Test
    void transactionPersistsSnapshotsAndMovesThroughFailureSafeStates() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            BulkMiningTransactionRepository repository = new BulkMiningTransactionRepository(database);
            UUID worldId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            BulkMiningTransactionRecord record = new BulkMiningTransactionRecord("tx-bulk-1", playerId,
                    "ADVANCED_ENCHANTMENTS", "a", BulkMiningTransactionState.VALIDATED, 2, 0,
                    "tx-bulk-1-custom-drops", List.of(
                    new BulkBlockSnapshot(worldId, 1, 64, 1, "STONE", "minecraft:stone", ""),
                    new BulkBlockSnapshot(worldId, 2, 64, 1, "DIAMOND_ORE",
                            "minecraft:diamond_ore", "itemsadder:mine:diamond")),
                    "frozen-command-payload", now, now, 0, "");

            assertTrue(repository.create(record).get(5, TimeUnit.SECONDS));
            assertFalse(repository.create(record).get(5, TimeUnit.SECONDS));
            assertEquals(1L, repository.countIncomplete().get(5, TimeUnit.SECONDS));

            BulkMiningTransactionRecord loaded = repository.incomplete(10).get(5, TimeUnit.SECONDS).getFirst();
            assertEquals(2, loaded.blockSnapshots().size());
            assertEquals("itemsadder:mine:diamond", loaded.blockSnapshots().get(1).customBlockId());

            assertTrue(repository.markState("tx-bulk-1", BulkMiningTransactionState.BLOCKS_MUTATED,
                    2, "tx-bulk-1-custom-drops", "").get(5, TimeUnit.SECONDS));
            assertTrue(repository.markState("tx-bulk-1", BulkMiningTransactionState.ROLLING_BACK,
                    1, "tx-bulk-1-custom-drops", "command failed").get(5, TimeUnit.SECONDS));
            assertTrue(repository.markState("tx-bulk-1", BulkMiningTransactionState.ROLLED_BACK,
                    1, "tx-bulk-1-custom-drops", "restored").get(5, TimeUnit.SECONDS));
            assertEquals(0L, repository.countIncomplete().get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void repeatedRecoveryMarksOnlyIncompleteTransactionsRecoverable() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            BulkMiningTransactionRepository repository = new BulkMiningTransactionRepository(database);
            long now = System.currentTimeMillis();
            UUID playerId = UUID.randomUUID();
            BulkMiningTransactionRecord record = new BulkMiningTransactionRecord("tx-recover", playerId,
                    "DRILL", "mine-a", BulkMiningTransactionState.BLOCKS_MUTATED, 1, 1, "",
                    List.of(new BulkBlockSnapshot(UUID.randomUUID(), 0, 64, 0, "STONE",
                            "minecraft:stone", "")), "", now, now, 0, "");

            assertTrue(repository.create(record).get(5, TimeUnit.SECONDS));
            assertEquals(1, repository.markRecoverable("tx-recover", "restart saw missing reward state")
                    .get(5, TimeUnit.SECONDS));
            assertEquals(1L, repository.countIncomplete().get(5, TimeUnit.SECONDS));
            assertTrue(repository.markState("tx-recover", BulkMiningTransactionState.FAILED_PERMANENT,
                    1, "", "staff closed").get(5, TimeUnit.SECONDS));
            assertEquals(0L, repository.countIncomplete().get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void snapshotEncodingRoundTripsWithoutPerBlockRows() {
        UUID worldId = UUID.randomUUID();
        List<BulkBlockSnapshot> snapshots = List.of(
                new BulkBlockSnapshot(worldId, -10, 5, 999, "STONE",
                        "minecraft:stone[axis=y]", ""),
                new BulkBlockSnapshot(worldId, 1, 2, 3, "EMERALD_ORE",
                        "minecraft:emerald_ore", "itemsadder:relic:emerald"));

        String encoded = BulkBlockSnapshot.encodeList(snapshots);
        List<BulkBlockSnapshot> decoded = BulkBlockSnapshot.decodeList(encoded);

        assertEquals(snapshots, decoded);
        assertFalse(encoded.contains(System.lineSeparator()));
    }

    @Test
    void rewardDeliveryPersistsDroppedEntitiesAndCannotTransitionTwice() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            BulkMiningTransactionRepository repository = new BulkMiningTransactionRepository(database);
            UUID playerId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            BulkMiningTransactionRecord record = new BulkMiningTransactionRecord("tx-drops", playerId,
                    "DRILL", "mine-a", BulkMiningTransactionState.REWARD_DELIVERING, 1, 1, "pkg",
                    List.of(new BulkBlockSnapshot(UUID.randomUUID(), 1, 2, 3, "STONE",
                            "minecraft:stone", "")), "commands", now, now, 0, "");
            assertTrue(repository.create(record).get(5, TimeUnit.SECONDS));

            assertTrue(repository.markRewardDelivered("tx-drops", 1, "pkg", "commands", List.of(entityId))
                    .get(5, TimeUnit.SECONDS));
            assertFalse(repository.markRewardDelivered("tx-drops", 1, "pkg", "commands", List.of(entityId))
                    .get(5, TimeUnit.SECONDS));
            BulkMiningTransactionRecord recovered = repository.incomplete(10).get(5, TimeUnit.SECONDS).getFirst();
            assertEquals(BulkMiningTransactionState.REWARD_DELIVERED, recovered.state());
            assertEquals(List.of(entityId), recovered.spawnedEntityIds());
        }
    }

    private DatabaseManager database() {
        return new DatabaseManager(Logger.getLogger("test"), new StorageConfig(StorageConfig.Type.SQLITE,
                temp.resolve("bulk.db"), "localhost", 3306, "db", "user", "", "useSSL=false",
                1, 100, 0, 1, 15, 20, 10));
    }
}
