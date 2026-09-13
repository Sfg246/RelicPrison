package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.reset.MineRuntimeRepository;
import site.mcrelicworld.relicprison.reset.ResetFailureRecord;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineRuntimeRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void resetFailuresPersistAndCanBeMarkedNotRetryable() throws Exception {
        DatabaseManager database = new DatabaseManager(Logger.getLogger("RelicPrisonTest"),
                storage(temporaryDirectory.resolve("runtime.db")));
        try {
            database.initializeAsync().get(5, TimeUnit.SECONDS);
            MineRuntimeRepository repository = new MineRuntimeRepository(database);
            ResetFailureRecord failure = new ResetFailureRecord("reset-1", "a", "manual", "resetting",
                    100L, 250L, 150L, 10L, 4L, 3L, 1L, 0L, 7L, "boom", true);

            repository.saveFailure(failure).get(5, TimeUnit.SECONDS);
            Optional<ResetFailureRecord> loaded = repository.latestRetryableFailure("a").get(5, TimeUnit.SECONDS);
            assertTrue(loaded.isPresent());
            assertEquals("reset-1", loaded.get().resetId());
            assertEquals(7L, loaded.get().resetCountAtFailure());

            repository.markFailureNotRetryable("reset-1").get(5, TimeUnit.SECONDS);
            assertTrue(repository.latestRetryableFailure("a").get(5, TimeUnit.SECONDS).isEmpty());
        } finally {
            database.close();
        }
    }

    private static StorageConfig storage(Path sqliteFile) {
        return new StorageConfig(StorageConfig.Type.SQLITE, sqliteFile,
                "localhost", 3306, "relicprison", "relicprison", "",
                "useSSL=false&serverTimezone=UTC", 1, 100, 0, 1, 15, 20, 1);
    }
}
