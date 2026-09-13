package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.booster.ActiveBooster;
import site.mcrelicworld.relicprison.booster.BoosterRepository;
import site.mcrelicworld.relicprison.config.StorageConfig;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BoosterRepositoryPersistenceTest {
    @TempDir Path directory;

    @Test
    void scheduledAndDisabledBoostersSurviveRestart() throws Exception {
        Path file = directory.resolve("boosters.db");
        long now = System.currentTimeMillis();
        UUID owner = UUID.randomUUID();
        ActiveBooster scheduled = new ActiveBooster("scheduled-test", owner, new BigDecimal("2.5"),
                now + 120_000L, now, false, "admin", now + 60_000L, true);
        try (DatabaseManager database = database(file)) {
            BoosterRepository repository = new BoosterRepository(database);
            repository.save(scheduled).get(5, TimeUnit.SECONDS);
        }
        try (DatabaseManager database = database(file)) {
            BoosterRepository repository = new BoosterRepository(database);
            ActiveBooster loaded = repository.loadActive(now).get(5, TimeUnit.SECONDS).getFirst();
            assertTrue(loaded.scheduled(now));
            assertEquals(scheduled.startsAt(), loaded.startsAt());
            repository.save(new ActiveBooster(loaded.id(), loaded.owner(), loaded.multiplier(), loaded.expiresAt(),
                    loaded.createdAt(), false, loaded.activatedBy(), loaded.startsAt(), false)).get(5, TimeUnit.SECONDS);
        }
        try (DatabaseManager database = database(file)) {
            ActiveBooster loaded = new BoosterRepository(database).loadActive(now).get(5, TimeUnit.SECONDS).getFirst();
            assertFalse(loaded.enabled());
        }
    }

    @Test
    void activeBoosterAndAtomicScopeChangeSurviveRestart() throws Exception {
        Path file = directory.resolve("scope-change.db");
        long now = System.currentTimeMillis();
        ActiveBooster active = new ActiveBooster("active-test", null, new BigDecimal("3"),
                now + 120_000L, now - 1_000L, true, "admin", now - 1_000L, true);
        UUID owner = UUID.randomUUID();
        ActiveBooster personal = new ActiveBooster(active.id(), owner, active.multiplier(), active.expiresAt(),
                active.createdAt(), false, active.activatedBy(), active.startsAt(), true);
        try (DatabaseManager database = database(file)) {
            BoosterRepository repository = new BoosterRepository(database);
            repository.save(active).get(5, TimeUnit.SECONDS);
            repository.replace(active, personal).get(5, TimeUnit.SECONDS);
        }
        try (DatabaseManager database = database(file)) {
            List<ActiveBooster> loaded = new BoosterRepository(database).loadActive(now).get(5, TimeUnit.SECONDS);
            assertEquals(1, loaded.size());
            assertEquals(owner, loaded.getFirst().owner());
            assertFalse(loaded.getFirst().serverWide());
            assertTrue(loaded.getFirst().active(now));
        }
    }

    private static DatabaseManager database(Path file) throws Exception {
        DatabaseManager database = new DatabaseManager(Logger.getLogger("BoosterRepositoryPersistenceTest"),
                new StorageConfig(StorageConfig.Type.SQLITE, file, "localhost", 3306, "test", "test", "",
                        "", 1, 100, 0, 1, 15, 20, 5));
        database.initializeAsync().get(5, TimeUnit.SECONDS);
        return database;
    }
}
