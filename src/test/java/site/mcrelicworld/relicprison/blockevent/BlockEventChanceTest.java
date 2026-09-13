package site.mcrelicworld.relicprison.blockevent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BlockEventChanceTest {
    @Test
    void chancePerBlockUsesCompleteOperationProbability() {
        assertEquals(0.0D, BlockEventService.chanceAnyBlockProbability(0.25D, 0));
        assertEquals(1.0D, BlockEventService.chanceAnyBlockProbability(1.0D, 10));
        assertEquals(0.25D, BlockEventService.chanceAnyBlockProbability(0.25D, 1), 0.0000001D);
        assertEquals(1.0D - Math.pow(0.75D, 600),
                BlockEventService.chanceAnyBlockProbability(0.25D, 600), 0.0000001D);
    }
}
