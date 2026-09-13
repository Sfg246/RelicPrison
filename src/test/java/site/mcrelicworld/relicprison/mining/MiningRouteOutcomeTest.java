package site.mcrelicworld.relicprison.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MiningRouteOutcomeTest {
    @Test
    void partialPickupOverflowIsNotAutoPickup() {
        MiningRouteOutcome result = new MiningRouteOutcome(0, 7, 1, false);
        assertFalse(result.autoPickupBlock());
        assertFalse(result.autoSellBlock());
    }

    @Test
    void unsellableDropIsNotAutoSell() {
        MiningRouteOutcome result = new MiningRouteOutcome(0, 0, 4, false);
        assertFalse(result.autoSellBlock());
        assertFalse(result.autoPickupBlock());
    }

    @Test
    void partialAutoSellCountsOnlyTheActualSaleRoute() {
        MiningRouteOutcome result = new MiningRouteOutcome(3, 0, 2, false);
        assertTrue(result.autoSellBlock());
        assertFalse(result.autoPickupBlock());
    }

    @Test
    void failedAutoBlockDoesNotCount() {
        MiningRouteOutcome result = new MiningRouteOutcome(0, 5, 0, false);
        assertFalse(result.autoBlockApplied());
        assertTrue(result.autoPickupBlock());
    }

    @Test
    void mixedSoldAndPickedOutputHasOnePrimaryDeliveryRoute() {
        MiningRouteOutcome result = new MiningRouteOutcome(2, 3, 0, true);
        assertTrue(result.autoSellBlock());
        assertFalse(result.autoPickupBlock());
        assertTrue(result.autoBlockApplied());
    }
}
