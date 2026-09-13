package site.mcrelicworld.relicprison.mining;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Frozen facts observed after bulk reward routing completed successfully. */
public record BulkMiningDeliveryResult(
        BigDecimal moneyEarned,
        long itemsSold,
        List<UUID> droppedEntityIds,
        Map<Integer, MiningRouteOutcome> blockRoutes
) {
    public BulkMiningDeliveryResult {
        moneyEarned = moneyEarned == null ? BigDecimal.ZERO : moneyEarned;
        itemsSold = Math.max(0L, itemsSold);
        droppedEntityIds = List.copyOf(droppedEntityIds == null ? List.of() : droppedEntityIds);
        blockRoutes = Map.copyOf(blockRoutes == null ? Map.of() : blockRoutes);
    }

    public long autoSellBlocks() {
        return blockRoutes.values().stream().filter(MiningRouteOutcome::autoSellBlock).count();
    }

    public long autoPickupBlocks() {
        return blockRoutes.values().stream().filter(MiningRouteOutcome::autoPickupBlock).count();
    }

    public long autoBlockBlocks() {
        return blockRoutes.values().stream().filter(MiningRouteOutcome::autoBlockApplied).count();
    }

    public long fallbackDroppedItems() {
        return blockRoutes.values().stream().mapToLong(MiningRouteOutcome::droppedItems).sum();
    }
}
