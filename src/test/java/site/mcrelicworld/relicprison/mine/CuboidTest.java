package site.mcrelicworld.relicprison.mine;

import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.api.model.BlockPosition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidTest {
    @Test void normalizesReversedCornersAndCalculatesInclusiveVolume() {
        Cuboid cuboid = new Cuboid(new BlockPosition(10, 20, 30), new BlockPosition(1, 2, 3));
        assertEquals(new BlockPosition(1, 2, 3), cuboid.minimum());
        assertEquals(new BlockPosition(10, 20, 30), cuboid.maximum());
        assertEquals(10L * 19L * 28L, cuboid.volume());
        assertTrue(cuboid.contains(5, 10, 10));
        assertFalse(cuboid.contains(11, 10, 10));
    }
}
