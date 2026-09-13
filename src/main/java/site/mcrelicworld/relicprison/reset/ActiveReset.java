package site.mcrelicworld.relicprison.reset;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Location;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.mine.composition.BlockTypeRef;
import site.mcrelicworld.relicprison.mine.composition.CompositionEntry;
import site.mcrelicworld.relicprison.mine.composition.MineComposition;
import site.mcrelicworld.relicprison.integration.ItemsAdderIntegration;

final class ActiveReset {
    private final MineDefinition mine;
    private final MineRuntime runtime;
    private final World world;
    private final String reason;
    private final ResetCursor cursor;
    private final ItemsAdderIntegration itemsAdder;
    private final MineComposition composition;
    private final Location scratchLocation;
    private final long startedAt;
    private long processed;
    private long vanillaPlaced;
    private long itemsAdderPlaced;
    private long airPlaced;
    private int budget;
    private int itemsAdderBudget;

    ActiveReset(MineDefinition mine, MineRuntime runtime, World world, String reason, int initialBudget,
                int initialItemsAdderBudget, ItemsAdderIntegration itemsAdder, MineComposition composition) {
        this.mine = mine;
        this.runtime = runtime;
        this.world = world;
        this.reason = reason;
        this.cursor = new ResetCursor(mine.bounds(), mine.resetConfig().order());
        this.itemsAdder = itemsAdder;
        this.composition = composition;
        this.scratchLocation = new Location(world, 0, 0, 0);
        this.startedAt = System.currentTimeMillis();
        this.budget = initialBudget;
        this.itemsAdderBudget = initialItemsAdderBudget;
    }

    TickResult tick(long targetNanos, int minimumBudget, int maximumBudget, long itemsAdderTargetNanos,
                    int minimumItemsAdderBudget, int maximumItemsAdderBudget) {
        long start = System.nanoTime();
        int placed = 0;
        long itemsAdderStart = 0L;
        int placedItemsAdder = 0;
        while (cursor.hasNext() && placed < budget && System.nanoTime() - start < targetNanos) {
            CompositionEntry entry = composition.compiled().sampleEntry();
            BlockTypeRef selected = entry.block();
            Block block = world.getBlockAt(cursor.x(), cursor.y(), cursor.z());
            if (selected.kind() == BlockTypeRef.Kind.ITEMSADDER) {
                if (placedItemsAdder >= itemsAdderBudget) break;
                if (itemsAdderStart == 0L) itemsAdderStart = System.nanoTime();
                if (System.nanoTime() - itemsAdderStart >= itemsAdderTargetNanos) break;
                setScratchLocation();
                if (itemsAdder == null || !itemsAdder.connected() || !itemsAdder.placeBlock(selected.id(), scratchLocation)) {
                    Material fallback = entry.fallbackMaterial().orElseThrow(() ->
                            new IllegalStateException("Unable to place ItemsAdder block " + selected.id()));
                    block.setType(fallback, false);
                    vanillaPlaced++;
                } else {
                    placedItemsAdder++;
                    itemsAdderPlaced++;
                }
            } else {
                setScratchLocation();
                if (itemsAdder != null && itemsAdder.connected()) itemsAdder.removeBlock(scratchLocation);
                Material material = selected.airBlock() ? Material.AIR : selected.material();
                block.setType(material, false);
                if (selected.airBlock()) airPlaced++;
                else vanillaPlaced++;
            }
            cursor.advance();
            placed++;
            processed++;
        }
        long elapsed = Math.max(1, System.nanoTime() - start);
        long itemsAdderElapsed = itemsAdderStart == 0L ? 0L : Math.max(1, System.nanoTime() - itemsAdderStart);
        if (placed > 0) {
            if (elapsed < targetNanos * 0.70) budget = Math.min(maximumBudget, Math.max(budget + 1, (int) (budget * 1.15)));
            else if (elapsed > targetNanos) budget = Math.max(minimumBudget, (int) (budget * 0.75));
        }
        if (placedItemsAdder > 0) {
            if (itemsAdderElapsed < itemsAdderTargetNanos * 0.70) {
                itemsAdderBudget = Math.min(maximumItemsAdderBudget, Math.max(itemsAdderBudget + 1, (int) (itemsAdderBudget * 1.15)));
            } else if (itemsAdderElapsed > itemsAdderTargetNanos) {
                itemsAdderBudget = Math.max(minimumItemsAdderBudget, (int) (itemsAdderBudget * 0.75));
            }
        }
        return new TickResult(!cursor.hasNext(), placed, placedItemsAdder, elapsed, itemsAdderElapsed, budget, itemsAdderBudget);
    }

    MineDefinition mine() { return mine; }
    MineRuntime runtime() { return runtime; }
    String reason() { return reason; }
    long processed() { return processed; }
    long vanillaPlaced() { return vanillaPlaced; }
    long itemsAdderPlaced() { return itemsAdderPlaced; }
    long airPlaced() { return airPlaced; }
    long startedAt() { return startedAt; }

    private void setScratchLocation() {
        scratchLocation.setX(cursor.x());
        scratchLocation.setY(cursor.y());
        scratchLocation.setZ(cursor.z());
    }

    record TickResult(boolean complete, int placed, int itemsAdderPlaced, long elapsedNanos,
                      long itemsAdderElapsedNanos, int nextBudget, int nextItemsAdderBudget) {}
}
