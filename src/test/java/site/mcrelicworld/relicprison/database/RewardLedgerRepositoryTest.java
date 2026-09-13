package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.reward.RewardComponentRecord;
import site.mcrelicworld.relicprison.reward.RewardComponentState;
import site.mcrelicworld.relicprison.reward.RewardComponentType;
import site.mcrelicworld.relicprison.reward.RewardLedgerRepository;
import site.mcrelicworld.relicprison.reward.RewardPackageRecord;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RewardLedgerRepositoryTest {
    @TempDir Path temp;

    @Test
    void packageAndComponentsAreIdempotentAndRunningRecoversAmbiguous() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            RewardLedgerRepository repository = new RewardLedgerRepository(database);
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            RewardPackageRecord rewardPackage = new RewardPackageRecord("pkg-1", "test", "op-1", playerId,
                    RewardComponentState.PENDING, now, now, "{\"frozen\":true}", "");
            RewardComponentRecord command = new RewardComponentRecord("pkg-1", "command-1", "test", "op-1",
                    playerId, "say hi", RewardComponentType.CONSOLE_COMMAND, BigDecimal.ZERO, now,
                    RewardComponentState.PENDING, 0, now, 0L, 0L, "", "pkg-1:command-1",
                    "", "", 0L, 0L, 0L, 0L);
            RewardComponentRecord money = new RewardComponentRecord("pkg-1", "money-1", "test", "op-1",
                    playerId, "", RewardComponentType.MONEY, new BigDecimal("10.00"), now,
                    RewardComponentState.PENDING, 0, now, 0L, 0L, "", "pkg-1:money-1",
                    "", "", 0L, 0L, 0L, 0L);

            assertTrue(repository.createPackage(rewardPackage, List.of(command, money)).get(5, TimeUnit.SECONDS));
            assertFalse(repository.createPackage(rewardPackage, List.of(command, money)).get(5, TimeUnit.SECONDS));
            assertEquals(2, repository.components("pkg-1").get(5, TimeUnit.SECONDS).size());

            repository.mark("pkg-1", "command-1", RewardComponentState.RUNNING, 0, "").get(5, TimeUnit.SECONDS);
            assertEquals(1, repository.markRunningAmbiguous("restart").get(5, TimeUnit.SECONDS));
            assertEquals(RewardComponentState.AMBIGUOUS,
                    repository.component("pkg-1", "command-1").get(5, TimeUnit.SECONDS).orElseThrow().state());
            assertEquals(1, repository.due(System.currentTimeMillis(), 10).get(5, TimeUnit.SECONDS).size());
            assertEquals(1, repository.failed(10).get(5, TimeUnit.SECONDS).size());
        }
    }

    @Test
    void oneComponentCanOnlyBeClaimedByOneWorkerAndOldTokenCannotComplete() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            RewardLedgerRepository repository = new RewardLedgerRepository(database);
            long now = System.currentTimeMillis();
            UUID playerId = UUID.randomUUID();
            RewardPackageRecord rewardPackage = new RewardPackageRecord("pkg-claim", "test", "op-claim",
                    playerId, RewardComponentState.PENDING, now, now, "frozen", "");
            RewardComponentRecord component = component("pkg-claim", "money", playerId, now);
            assertTrue(repository.createPackage(rewardPackage, List.of(component)).get(5, TimeUnit.SECONDS));

            CompletableFuture<Optional<RewardLedgerRepository.ClaimedComponent>> first =
                    repository.claim("pkg-claim", "money", "scheduler-a", now, 60_000L);
            CompletableFuture<Optional<RewardLedgerRepository.ClaimedComponent>> second =
                    repository.claim("pkg-claim", "money", "scheduler-b", now, 60_000L);
            List<Optional<RewardLedgerRepository.ClaimedComponent>> results =
                    CompletableFuture.allOf(first, second).thenApply(ignored -> List.of(first.join(), second.join()))
                            .get(5, TimeUnit.SECONDS);

            assertEquals(1, results.stream().filter(Optional::isPresent).count());
            RewardLedgerRepository.ClaimedComponent winner = results.stream()
                    .filter(Optional::isPresent).findFirst().orElseThrow().orElseThrow();
            assertTrue(repository.markClaimRunning(winner).get(5, TimeUnit.SECONDS));
            assertTrue(repository.completeClaim(winner, RewardComponentState.DELIVERED, "", 0L)
                    .get(5, TimeUnit.SECONDS));
            assertEquals(RewardComponentState.DELIVERED,
                    repository.component("pkg-claim", "money").get(5, TimeUnit.SECONDS).orElseThrow().state());

            RewardLedgerRepository.ClaimedComponent stale = new RewardLedgerRepository.ClaimedComponent(
                    winner.component(), "stale-token");
            assertFalse(repository.completeClaim(stale, RewardComponentState.RETRY_READY, "stale", now)
                    .get(5, TimeUnit.SECONDS));
            assertEquals(RewardComponentState.COMPLETED,
                    repository.packageById("pkg-claim").get(5, TimeUnit.SECONDS).orElseThrow().state());
        }
    }

    @Test
    void expiredClaimMovesToAmbiguousAndDoesNotAutoClaim() throws Exception {
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            RewardLedgerRepository repository = new RewardLedgerRepository(database);
            long now = System.currentTimeMillis();
            UUID playerId = UUID.randomUUID();
            RewardPackageRecord rewardPackage = new RewardPackageRecord("pkg-expire", "test", "op-expire",
                    playerId, RewardComponentState.PENDING, now, now, "frozen", "");
            assertTrue(repository.createPackage(rewardPackage, List.of(component("pkg-expire", "money", playerId, now)))
                    .get(5, TimeUnit.SECONDS));
            Optional<RewardLedgerRepository.ClaimedComponent> claim =
                    repository.claim("pkg-expire", "money", "worker", now, 1L).get(5, TimeUnit.SECONDS);
            assertTrue(claim.isPresent());

            assertEquals(1, repository.recoverExpiredClaims(now + 2_000L, "lease expired").get(5, TimeUnit.SECONDS));
            assertEquals(RewardComponentState.AMBIGUOUS,
                    repository.component("pkg-expire", "money").get(5, TimeUnit.SECONDS).orElseThrow().state());
            assertTrue(repository.claimDue(now + 2_000L, "worker-2", 60_000L, 10)
                    .get(5, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void repeatedRestartAndReconnectRecoveryKeepsBulkEffectsSingle() throws Exception {
        UUID playerId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            RewardLedgerRepository repository = new RewardLedgerRepository(database);
            RewardPackageRecord rewardPackage = new RewardPackageRecord("pkg-bulk", "bulk-mining", "bulk-op",
                    playerId, RewardComponentState.PENDING, now, now, "frozen", "");
            List<RewardComponentRecord> components = List.of(
                    component("pkg-bulk", "mining-xp", "bulk-mining", playerId, now,
                            RewardComponentType.EXPERIENCE),
                    component("pkg-bulk", "autosell-money", "bulk-mining", playerId, now,
                            RewardComponentType.MONEY),
                    component("pkg-bulk", "custom-drop-command-0", "bulk-mining", playerId, now,
                            RewardComponentType.CONSOLE_COMMAND));
            assertTrue(repository.createPackage(rewardPackage, components).get(5, TimeUnit.SECONDS));
            for (RewardComponentRecord component : components) {
                RewardLedgerRepository.ClaimedComponent claim = repository.claim("pkg-bulk",
                        component.componentId(), "worker", now, 60_000L).get(5, TimeUnit.SECONDS).orElseThrow();
                assertTrue(repository.markClaimRunning(claim).get(5, TimeUnit.SECONDS));
            }

            RewardLedgerRepository.InterruptedRecovery recovered = repository
                    .recoverInterruptedComponents("restart").get(5, TimeUnit.SECONDS);
            assertEquals(2, recovered.retryable());
            assertEquals(0, recovered.assumedDelivered());
            assertEquals(1, recovered.ambiguous());
            assertEquals(RewardComponentState.RETRY_READY,
                    repository.component("pkg-bulk", "mining-xp").get(5, TimeUnit.SECONDS).orElseThrow().state());
            assertEquals(RewardComponentState.RETRY_READY,
                    repository.component("pkg-bulk", "autosell-money").get(5, TimeUnit.SECONDS).orElseThrow().state());
            assertEquals(RewardComponentState.AMBIGUOUS,
                    repository.component("pkg-bulk", "custom-drop-command-0").get(5, TimeUnit.SECONDS)
                            .orElseThrow().state());

            RewardLedgerRepository.InterruptedRecovery repeated = repository
                    .recoverInterruptedComponents("second restart").get(5, TimeUnit.SECONDS);
            assertEquals(new RewardLedgerRepository.InterruptedRecovery(0, 0, 0), repeated);
        }

        try (DatabaseManager database = database()) {
            database.initializeAsync().get(10, TimeUnit.SECONDS);
            RewardLedgerRepository repository = new RewardLedgerRepository(database);
            RewardLedgerRepository.InterruptedRecovery reconnect = repository
                    .recoverInterruptedComponents("database reconnect").get(5, TimeUnit.SECONDS);
            assertEquals(new RewardLedgerRepository.InterruptedRecovery(0, 0, 0), reconnect);
            assertEquals(RewardComponentState.AMBIGUOUS,
                    repository.component("pkg-bulk", "custom-drop-command-0").get(5, TimeUnit.SECONDS)
                            .orElseThrow().state());
        }
    }

    private RewardComponentRecord component(String packageId, String componentId, UUID playerId, long now) {
        return component(packageId, componentId, "test", playerId, now, RewardComponentType.MONEY);
    }

    private RewardComponentRecord component(String packageId, String componentId, String sourceSystem,
                                             UUID playerId, long now, RewardComponentType type) {
        return new RewardComponentRecord(packageId, componentId, sourceSystem, "op",
                playerId, "", type, new BigDecimal("10.00"), now,
                RewardComponentState.PENDING, 0, now, 0L, 0L, "", packageId + ':' + componentId,
                "", "", 0L, 0L, 0L, 0L);
    }

    private DatabaseManager database() {
        return new DatabaseManager(Logger.getLogger("test"), new StorageConfig(StorageConfig.Type.SQLITE,
                temp.resolve("data.db"), "localhost", 3306, "db", "user", "", "useSSL=false",
                1, 100, 0, 1, 15, 20, 10));
    }
}
