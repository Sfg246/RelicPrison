package site.mcrelicworld.relicprison.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhaseSevenEightSourceTest {
    @Test
    void diagnosticsUseRealServicesAndRedactedExports() throws Exception {
        String diagnostics = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/admin/DiagnosticServiceImpl.java"));

        assertTrue(diagnostics.contains("plugin.database().health()"));
        assertTrue(diagnostics.contains("dirtyProfileCount"));
        assertTrue(diagnostics.contains("slowResetSliceCount"));
        assertTrue(diagnostics.contains("failed-progression-transactions"));
        assertTrue(diagnostics.contains("REDACTION-REPORT.txt"));
        assertTrue(diagnostics.contains("RedactionService"));
        assertFalse(diagnostics.contains("addSanitizedStorage"));
    }

    @Test
    void backupsVerifyBeforeRestoreAndUseStaging() throws Exception {
        String backup = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/admin/BackupServiceImpl.java"));

        assertTrue(backup.contains("verifyArchive"));
        assertTrue(backup.contains("restore-staging-"));
        assertTrue(backup.contains("createSafetyBackup"));
        assertTrue(backup.contains("requestRestoreVerified"));
        assertTrue(backup.contains("delete(String id, boolean confirmed)"));
        assertFalse(backup.contains("dataFolder.resolve(entry.getName()).normalize();"));
    }

    @Test
    void auditLedgerAndCommandMonitorArePresent() throws Exception {
        String database = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/database/DatabaseManager.java"));
        String monitor = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/admin/CommandDispatchMonitor.java"));

        assertTrue(database.contains("rp_staff_audit"));
        assertTrue(database.contains("SUPPORTED_SCHEMA_VERSION = 17"));
        assertTrue(monitor.contains("failedCommands"));
        assertTrue(monitor.contains("RedactionService.redactInline"));
    }

    @Test
    void miningHotPathStillAvoidsDatabaseAndFutureWaits() throws Exception {
        String mining = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/mining/MiningServiceImpl.java"));
        String resets = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/reset/MineResetServiceImpl.java"));

        assertFalse(mining.contains("java.sql"));
        assertFalse(mining.contains("CompletableFuture"));
        assertFalse(mining.contains(".join()"));
        assertTrue(mining.contains("recordNanos(\"mining.normal\""));
        assertTrue(mining.contains("recordNanos(\"mining.bulk\""));
        assertTrue(resets.contains("WorldUnloadEvent"));
        assertTrue(resets.contains("World unloaded during reset"));
    }
}
