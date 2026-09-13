package site.mcrelicworld.relicprison.reset;

import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.api.model.BlockPosition;
import site.mcrelicworld.relicprison.mine.Cuboid;
import site.mcrelicworld.relicprison.mine.MineResetConfig;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetCursorTest {
    @Test
    void visitsEveryCoordinateExactlyOnceAcrossNegativeChunks() {
        Cuboid bounds = new Cuboid(new BlockPosition(-18, 4, -2), new BlockPosition(17, 6, 18));
        ResetCursor cursor = new ResetCursor(bounds, MineResetConfig.ResetOrder.CHUNK_GROUPED_BOTTOM_UP);
        Set<String> positions = new HashSet<>();
        while (cursor.hasNext()) {
            assertTrue(positions.add(cursor.x() + ":" + cursor.y() + ":" + cursor.z()));
            cursor.advance();
        }
        assertEquals(bounds.volume(), (long) positions.size());
    }

    @Test
    void honorsVerticalOrder() {
        Cuboid bounds = new Cuboid(new BlockPosition(0, 2, 0), new BlockPosition(0, 4, 0));
        ResetCursor bottom = new ResetCursor(bounds, MineResetConfig.ResetOrder.CHUNK_GROUPED_BOTTOM_UP);
        assertEquals(2, bottom.y());
        bottom.advance();
        assertEquals(3, bottom.y());

        ResetCursor top = new ResetCursor(bounds, MineResetConfig.ResetOrder.CHUNK_GROUPED_TOP_DOWN);
        assertEquals(4, top.y());
        top.advance();
        assertEquals(3, top.y());
    }

    @Test
    void everyConfiguredOrderVisitsEachCoordinateOnce() {
        Cuboid bounds = new Cuboid(new BlockPosition(0, 0, 0), new BlockPosition(3, 2, 2));
        for (MineResetConfig.ResetOrder order : MineResetConfig.ResetOrder.values()) {
            ResetCursor cursor = new ResetCursor(bounds, order);
            Set<String> positions = new HashSet<>();
            while (cursor.hasNext()) {
                assertTrue(positions.add(cursor.x() + ":" + cursor.y() + ":" + cursor.z()), order.name());
                cursor.advance();
            }
            assertEquals(bounds.volume(), (long) positions.size(), order.name());
        }
    }
}
