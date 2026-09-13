package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.api.model.PlayerProfileView;
import site.mcrelicworld.relicprison.config.StorageConfig;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatabaseInitializationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void initializeAsyncCreatesSchemaOnDatabaseExecutor() throws Exception {
        DatabaseManager database = new DatabaseManager(Logger.getLogger("RelicPrisonTest"),
                storage(temporaryDirectory.resolve("profiles.db"), 1));
        try {
            database.initializeAsync().get(5, TimeUnit.SECONDS);
            int schemaVersion = database.submit(connection -> {
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery("SELECT MAX(version) FROM rp_schema_version")) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }).get(5, TimeUnit.SECONDS);
            String workerThread = database.submit(connection -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);

            assertEquals(DatabaseManager.SUPPORTED_SCHEMA_VERSION, schemaVersion);
            assertEquals("RelicPrison-Database", workerThread);
        } finally {
            database.close();
        }
    }

    @Test
    void duplicateProfileLoadRequestsReuseTheInFlightFuture() throws Exception {
        DatabaseManager database = new DatabaseManager(Logger.getLogger("RelicPrisonTest"),
                storage(temporaryDirectory.resolve("profiles.db"), 1));
        try {
            database.initializeAsync().get(5, TimeUnit.SECONDS);
            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            CompletableFuture<Void> blocker = database.submit(connection -> {
                blockerStarted.countDown();
                assertTrue(releaseBlocker.await(5, TimeUnit.SECONDS));
                return null;
            });
            assertTrue(blockerStarted.await(5, TimeUnit.SECONDS));

            PlayerProfileRepository repository = new PlayerProfileRepository(database, ZoneId.of("UTC"), "a");
            UUID playerId = UUID.randomUUID();
            CompletableFuture<PlayerProfileView> first = repository.load(playerId, "First");
            CompletableFuture<PlayerProfileView> second = repository.load(playerId, "Second");

            assertSame(first, second);
            assertFalse(first.isDone());
            assertEquals(PlayerLoadState.LOADING, repository.loadState(playerId));

            releaseBlocker.countDown();
            PlayerProfileView profile = first.get(5, TimeUnit.SECONDS);
            blocker.get(5, TimeUnit.SECONDS);
            assertEquals(playerId, profile.uuid());
            assertEquals(PlayerLoadState.READY, repository.loadState(playerId));
        } finally {
            database.close();
        }
    }

    @Test
    void profileLoadTimeoutMarksStateTimedOut() throws Exception {
        DatabaseManager database = new DatabaseManager(Logger.getLogger("RelicPrisonTest"),
                storage(temporaryDirectory.resolve("profiles.db"), 1));
        try {
            database.initializeAsync().get(5, TimeUnit.SECONDS);
            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            CompletableFuture<Void> blocker = database.submit(connection -> {
                blockerStarted.countDown();
                assertTrue(releaseBlocker.await(5, TimeUnit.SECONDS));
                return null;
            });
            assertTrue(blockerStarted.await(5, TimeUnit.SECONDS));

            PlayerProfileRepository repository = new PlayerProfileRepository(database, ZoneId.of("UTC"), "a", 1);
            UUID playerId = UUID.randomUUID();
            CompletableFuture<PlayerProfileView> load = repository.load(playerId, "Timed");

            Throwable error = load.handle((ignored, failure) -> failure).get(3, TimeUnit.SECONDS);
            assertNotNull(error);
            assertEquals(PlayerLoadState.TIMED_OUT, repository.loadState(playerId));

            releaseBlocker.countDown();
            blocker.get(5, TimeUnit.SECONDS);
        } finally {
            database.close();
        }
    }

    @Test
    void closeWaitsForPendingWritesBeforeClosingPool() throws Exception {
        DatabaseManager database = new DatabaseManager(Logger.getLogger("RelicPrisonTest"),
                storage(temporaryDirectory.resolve("profiles.db"), 2));
        database.initializeAsync().get(5, TimeUnit.SECONDS);

        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        CompletableFuture<Void> pendingWrite = database.submitIdempotent(connection -> {
            writeStarted.countDown();
            assertTrue(releaseWrite.await(5, TimeUnit.SECONDS));
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_shutdown_test (id INTEGER PRIMARY KEY)");
            }
            return null;
        });
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS));

        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            releaseWrite.countDown();
        });
        releaser.start();

        database.close();
        releaser.join(1_000L);
        pendingWrite.get(5, TimeUnit.SECONDS);
        assertTrue(pendingWrite.isDone());
    }

    private static StorageConfig storage(Path sqliteFile, int shutdownTimeoutSeconds) {
        return new StorageConfig(StorageConfig.Type.SQLITE, sqliteFile,
                "localhost", 3306, "relicprison", "relicprison", "",
                "useSSL=false&serverTimezone=UTC", 1, 100, 0, 1, 15, 20, shutdownTimeoutSeconds);
    }
}
