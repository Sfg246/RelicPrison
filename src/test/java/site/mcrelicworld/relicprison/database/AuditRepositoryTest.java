package site.mcrelicworld.relicprison.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.mcrelicworld.relicprison.admin.AuditEntry;
import site.mcrelicworld.relicprison.admin.AuditQuery;
import site.mcrelicworld.relicprison.admin.AuditRepository;
import site.mcrelicworld.relicprison.config.StorageConfig;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuditRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void migrationCreatesAuditTableAndRepositoryFiltersEntries() throws Exception {
        DatabaseManager database = new DatabaseManager(Logger.getLogger("RelicPrisonAuditTest"),
                storage(temporaryDirectory.resolve("audit.db")));
        try {
            database.initializeAsync().get(5, TimeUnit.SECONDS);
            boolean tableExists = database.submit(connection -> {
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery(
                             "SELECT name FROM sqlite_master WHERE type='table' AND name='rp_staff_audit'")) {
                    return result.next();
                }
            }).get(5, TimeUnit.SECONDS);
            assertTrue(tableExists);

            AuditRepository repository = new AuditRepository(database);
            UUID staff = UUID.randomUUID();
            repository.record(new AuditEntry("audit-1", 1000L, staff, "Admin", "mine_edit",
                    "mine", "a", "before", "after", true, "test", "related", "safe"))
                    .get(5, TimeUnit.SECONDS);

            var entries = repository.query(new AuditQuery(1, 20, Optional.of(staff),
                    Optional.of("mine_edit"), Optional.of("mine"), Optional.of("a"),
                    Optional.empty(), Optional.empty())).get(5, TimeUnit.SECONDS);

            assertEquals(1, entries.size());
            assertEquals("audit-1", entries.getFirst().auditId());
        } finally {
            database.close();
        }
    }

    private static StorageConfig storage(Path sqliteFile) {
        return new StorageConfig(StorageConfig.Type.SQLITE, sqliteFile,
                "localhost", 3306, "relicprison", "relicprison", "",
                "useSSL=false&serverTimezone=UTC", 1, 100, 0, 1, 15, 20, 5);
    }
}
