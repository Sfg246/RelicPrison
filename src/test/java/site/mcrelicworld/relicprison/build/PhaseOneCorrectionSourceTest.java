package site.mcrelicworld.relicprison.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhaseOneCorrectionSourceTest {
    @Test void destinationIsValidatedBeforeMineRegistrationAndCopying() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/mine/MineStructureService.java"));
        int execute = source.indexOf("public CompletableFuture<StructureOperationRecord> execute");
        int retry = source.indexOf("public CompletableFuture<StructureOperationRecord> retry", execute);
        String method = source.substring(execute, retry);
        assertTrue(method.indexOf("requireEmptyDestination(prepared)")
                        < method.indexOf("registerDisabledTarget(prepared)"),
                "Destination validation must happen before target registration");
        assertTrue(method.indexOf("registerDisabledTarget(prepared)")
                        < method.indexOf("copyAndActivate"),
                "Target registration must be durable before physical copying");
    }

    @Test void playerLoadsAreOwnedByTheCurrentSession() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/database/PlayerProfileRepository.java"));
        assertTrue(source.contains("record LoadAttempt(long session"));
        assertTrue(source.contains("current.session() == session"));
        assertTrue(source.contains("isCurrentSession(playerId, attempt.session())"));
        assertTrue(source.contains("currentSession != null && !currentSession.equals(endedSession)"));
    }

    @Test void allReloadPreviewsSideFilesAndRestoresRuntimeOnApplyFailure() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/RelicPrisonPlugin.java"));
        assertTrue(source.contains("nextPrisonGui = prisonGuis.preview()"));
        assertTrue(source.contains("nextCustomDrops = miningService.previewCustomDrops()"));
        assertTrue(source.contains("restartOnlyReloadFailures(configManager.snapshot(), nextConfig)"));
        assertTrue(source.contains("restoreRuntimeAfterFailedReload(beforeMiningConfig)"));
    }
    @Test void optionalStructureProviderDoesNotCopyEntitiesAndRequiresAnEnabledPlugin() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/integration/WorldEditStructureProvider.java"));
        assertTrue(source.contains("plugin != null && plugin.isEnabled()"));
        assertTrue(source.contains("setCopyingEntities\", false"));
        assertTrue(source.contains("copyEntities\", false"));
        assertTrue(!source.contains("import com.sk89q.worldedit"),
                "WorldEdit must remain an optional reflective dependency");
    }

    @Test void reloadDoesNotStackReflectiveAdvancedEnchantmentsListeners() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/integration/AdvancedEnchantmentsIntegration.java"));
        assertTrue(source.contains("HandlerList.unregisterAll(runtimeListener)"));
    }

    @Test void structureLocksProtectRegionsAndResetStarts() throws Exception {
        String structures = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/mine/MineStructureService.java"));
        String resets = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/reset/MineResetServiceImpl.java"));
        assertTrue(structures.contains("intersectsLockedRegion(record.target().worldId()"));
        assertTrue(structures.contains("Structure destination overlaps existing mine"));
        assertTrue(resets.contains("mineStructures().isLocked(mine)"));
    }

}
