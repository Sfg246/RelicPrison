package site.mcrelicworld.relicprison.reset;

import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.api.model.MineResetState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineRuntimeTest {
    @Test
    void reconciliationOnlyMarksActualChangesDirty() {
        MineRuntime runtime = new MineRuntime("a", 100, 0, 0, 1000, 0, MineResetState.State.IDLE);
        runtime.reconcileVolume(100);
        assertFalse(runtime.dirty());

        runtime.reconcileVolume(50);
        assertTrue(runtime.dirty());
        assertEquals(50L, runtime.remainingBlocks());
    }

    @Test
    void brokenBlocksNeverMakeRemainingNegative() {
        MineRuntime runtime = new MineRuntime("a", 5, 0, 0, 1000, 0, MineResetState.State.IDLE);
        assertEquals(0L, runtime.recordBroken(10));
        assertEquals(100.0, runtime.minedPercentage(5), 0.001);
    }

    @Test
    void recountRepairCorrectsRemainingAndRecordsReason() {
        MineRuntime runtime = new MineRuntime("a", 5, 0, 0, 1000, 0, MineResetState.State.IDLE);
        runtime.recordBroken(4);
        runtime.repairRemaining(3, "manual", "Andre");

        assertEquals(3L, runtime.remainingBlocks());
        assertEquals("manual", runtime.lastRecountReason());
        assertEquals("Andre", runtime.lastRecountActor());
        assertTrue(runtime.lastRecountAt() > 0);
        assertTrue(runtime.dirty());
    }
}
