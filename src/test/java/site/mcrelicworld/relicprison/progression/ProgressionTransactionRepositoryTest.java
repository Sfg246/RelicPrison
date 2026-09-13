package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.progression.ProgressionExternalStatus;
import site.mcrelicworld.relicprison.progression.ProgressionOperationType;
import site.mcrelicworld.relicprison.progression.ProgressionRewardStatus;
import site.mcrelicworld.relicprison.progression.ProgressionTransactionRecord;
import site.mcrelicworld.relicprison.progression.ProgressionTransactionRepository;
import site.mcrelicworld.relicprison.progression.ProgressionTransactionState;

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

final class ProgressionTransactionRepositoryTest {
    @TempDir Path temp;

    @Test
    void activeTransactionPreventsDuplicateAndRewardsReserveOnce() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            ProgressionTransactionRepository repository = new ProgressionTransactionRepository(database);
            UUID playerId = UUID.randomUUID();
            ProgressionTransactionRecord first = transaction("tx-1", playerId, "a", "b");
            ProgressionTransactionRecord duplicate = transaction("tx-2", playerId, "a", "b");

            assertTrue(repository.createIfNoActive(first).get(5, TimeUnit.SECONDS).created());
            ProgressionTransactionRepository.CreateResult duplicateResult =
                    repository.createIfNoActive(duplicate).get(5, TimeUnit.SECONDS);
            assertFalse(duplicateResult.created());
            assertEquals("tx-1", duplicateResult.record().transactionId());

            repository.markWithdrawn("tx-1", new BigDecimal("100")).get(5, TimeUnit.SECONDS);
            repository.markProfileSaved("tx-1").get(5, TimeUnit.SECONDS);
            repository.markPermissionsUpdated("tx-1").get(5, TimeUnit.SECONDS);
            assertTrue(repository.reserveRewards("tx-1").get(5, TimeUnit.SECONDS));
            assertFalse(repository.reserveRewards("tx-1").get(5, TimeUnit.SECONDS));
            repository.markRewardsDelivered("tx-1").get(5, TimeUnit.SECONDS);
            repository.markCompleted("tx-1").get(5, TimeUnit.SECONDS);

            assertTrue(repository.pendingFor(playerId).get(5, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void requiredCheckpointsRemainRecoverableUntilTerminal() {
        assertTrue(ProgressionTransactionState.STARTED.needsRecovery());
        assertTrue(ProgressionTransactionState.MONEY_WITHDRAWN.needsRecovery());
        assertTrue(ProgressionTransactionState.WITHDRAWAL_CONFIRMED.needsRecovery());
        assertTrue(ProgressionTransactionState.PROFILE_SAVED.needsRecovery());
        assertTrue(ProgressionTransactionState.PERMISSIONS_UPDATED.needsRecovery());
        assertFalse(ProgressionTransactionState.WITHDRAWAL_IN_PROGRESS.needsRecovery());
        assertFalse(ProgressionTransactionState.WITHDRAWAL_AMBIGUOUS.needsRecovery());
        assertFalse(ProgressionTransactionState.COMPLETED.needsRecovery());
        assertFalse(ProgressionTransactionState.FAILED.needsRecovery());
        assertFalse(ProgressionTransactionState.MANUAL_REVIEW.needsRecovery());
        assertFalse(ProgressionTransactionState.STAFF_REVIEW.needsRecovery());
    }

    @Test
    void refundIntentCanOnlyBeClaimedByOneRecoveryWorker() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            ProgressionTransactionRepository repository = new ProgressionTransactionRepository(database);
            UUID playerId = UUID.randomUUID();
            assertTrue(repository.createIfNoActive(transaction("tx-refund", playerId, "a", "b"))
                    .get(5, TimeUnit.SECONDS).created());

            repository.recordRefundIntent("tx-refund", new BigDecimal("100"), "fault injection")
                    .get(5, TimeUnit.SECONDS);
            CompletableFuture<Boolean> first = repository.claimRefund("tx-refund");
            CompletableFuture<Boolean> second = repository.claimRefund("tx-refund");

            List<Boolean> results = CompletableFuture.allOf(first, second)
                    .thenApply(ignored -> List.of(first.join(), second.join()))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());

            repository.markRefundConfirmed("tx-refund").get(5, TimeUnit.SECONDS);
            assertEquals("REFUND_CONFIRMED", refundState(database, "tx-refund"));
            assertFalse(repository.claimRefund("tx-refund").get(5, TimeUnit.SECONDS));
        }
    }

    private static String refundState(DatabaseManager database, String transactionId) throws Exception {
        return database.submitIdempotent(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT refund_state FROM rp_progression_transactions WHERE transaction_id=?")) {
                statement.setString(1, transactionId);
                try (var result = statement.executeQuery()) {
                    return result.next() ? result.getString(1) : "";
                }
            }
        }).get(5, TimeUnit.SECONDS);
    }

    private DatabaseManager database() {
        return new DatabaseManager(Logger.getLogger("test"), new StorageConfig(StorageConfig.Type.SQLITE,
                temp.resolve("data.db"), "localhost", 3306, "db", "user", "", "useSSL=false",
                1, 100, 0, 1, 15, 20, 10));
    }

    private static ProgressionTransactionRecord transaction(String id, UUID playerId, String previousRank,
                                                            String targetRank) {
        long now = System.currentTimeMillis();
        return new ProgressionTransactionRecord(id, playerId, ProgressionOperationType.RANKUP, previousRank,
                null, targetRank, null, new BigDecimal("100"), BigDecimal.ZERO, now, now,
                ProgressionTransactionState.STARTED, 0, "", ProgressionRewardStatus.PENDING,
                ProgressionExternalStatus.PENDING, "idem-" + id, "");
    }
}
