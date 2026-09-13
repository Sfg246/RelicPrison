package site.mcrelicworld.relicprison.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PackedBlockKeyTest {
    @Test void distinguishesNegativeAndAdjacentCoordinates() {
        assertNotEquals(PackedBlockKey.pack(-18, -64, 17), PackedBlockKey.pack(-18, -63, 17));
        assertNotEquals(PackedBlockKey.pack(1, 0, 1), PackedBlockKey.pack(1, 0, 2));
    }
}
