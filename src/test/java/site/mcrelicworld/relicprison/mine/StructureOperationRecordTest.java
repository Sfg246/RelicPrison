package site.mcrelicworld.relicprison.mine;

import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.api.model.BlockPosition;
import site.mcrelicworld.relicprison.mine.composition.CompositionEntry;
import site.mcrelicworld.relicprison.mine.composition.BlockTypeRef;
import site.mcrelicworld.relicprison.mine.composition.MineComposition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureOperationRecordTest {
    @Test void preservesSnapshotsAndProgressAcrossStageChanges() {
        MineDefinition source = mine("a", 0);
        MineDefinition target = mine("b", 20);
        StructureOperationRecord record = new StructureOperationRecord(
                UUID.randomUUID(), StructureOperationType.COPY, StructureOperationStage.PREPARED,
                UUID.randomUUID(), source, target, 10L, 10L, 0L, 0L, "PENDING", null);

        StructureOperationRecord copied = record.withProgress(27L, 0L, "FAWE")
                .withStage(StructureOperationStage.DESTINATION_COPIED);

        assertEquals(source, copied.source());
        assertEquals(target, copied.target());
        assertEquals(27L, copied.copiedBlocks());
        assertEquals("FAWE", copied.provider());
        assertTrue(copied.recoverable());
        assertNull(copied.failure());
    }

    @Test void onlyCompletedAndRolledBackOperationsAreTerminal() {
        for (StructureOperationStage stage : StructureOperationStage.values()) {
            boolean expected = stage == StructureOperationStage.COMPLETED
                    || stage == StructureOperationStage.ROLLED_BACK;
            assertEquals(expected, stage.terminal(), stage.name());
        }
        assertFalse(StructureOperationStage.FAILED.terminal());
        assertTrue(StructureOperationStage.COMPLETED.terminal());
    }

    private static MineDefinition mine(String id, int offset) {
        MineComposition composition = new MineComposition(List.of(
                new CompositionEntry(BlockTypeRef.parse("STONE"), 100.0)));
        return new MineDefinition(id, id.toUpperCase(), UUID.randomUUID(), "world",
                new Cuboid(new BlockPosition(offset, 0, 0), new BlockPosition(offset + 2, 2, 2)),
                null, true, 0, null, null, null, Map.of(), composition,
                MineResetConfig.defaults(900, 80.0, List.of(30, 10, 5)));
    }
}
