package site.mcrelicworld.relicprison.mining;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BulkMiningDeliveryResultTest {
    @Test
    void mixedDeliveryRoutesUseActualOutcomesWithoutDoubleCounting() {
        BulkMiningDeliveryResult result = new BulkMiningDeliveryResult(new BigDecimal("8.25"), 3L, List.of(), Map.of(
                0, new MiningRouteOutcome(3, 2, 0, true),
                1, new MiningRouteOutcome(0, 5, 0, false),
                2, new MiningRouteOutcome(0, 4, 1, false),
                3, new MiningRouteOutcome(0, 0, 2, false)));

        assertEquals(1L, result.autoSellBlocks());
        assertEquals(1L, result.autoPickupBlocks());
        assertEquals(1L, result.autoBlockBlocks());
        assertEquals(3L, result.itemsSold());
        assertEquals(3L, result.fallbackDroppedItems());
        assertEquals(new BigDecimal("8.25"), result.moneyEarned());
    }

    @Test
    void failedAutoBlockAndPartialOverflowAreNotSuccessfulRoutes() {
        BulkMiningDeliveryResult result = new BulkMiningDeliveryResult(BigDecimal.ZERO, 0L, List.of(), Map.of(
                0, new MiningRouteOutcome(0, 7, 1, false)));

        assertEquals(0L, result.autoPickupBlocks());
        assertEquals(0L, result.autoSellBlocks());
        assertEquals(0L, result.autoBlockBlocks());
        assertEquals(1L, result.fallbackDroppedItems());
    }
}
