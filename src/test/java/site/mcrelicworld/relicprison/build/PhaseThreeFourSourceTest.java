package site.mcrelicworld.relicprison.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhaseThreeFourSourceTest {
    @Test
    void miningServiceDoesNotPerformSqlOrFutureWaitsInBlockPath() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/mining/MiningServiceImpl.java"));
        assertFalse(source.contains("java.sql"));
        assertFalse(source.contains("CompletableFuture"));
        assertFalse(source.contains(".join()"));
        assertTrue(source.contains("customDropCommands.merge"));
        assertTrue(source.contains("blockEvents().handle"));
    }

    @Test
    void progressionTransactionStatesContainRequiredCheckpoints() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/site/mcrelicworld/relicprison/progression/ProgressionTransactionState.java"));
        assertTrue(source.contains("STARTED"));
        assertTrue(source.contains("MONEY_WITHDRAWN"));
        assertTrue(source.contains("PROFILE_SAVED"));
        assertTrue(source.contains("PERMISSIONS_UPDATED"));
        assertTrue(source.contains("COMPLETED"));
    }
}
