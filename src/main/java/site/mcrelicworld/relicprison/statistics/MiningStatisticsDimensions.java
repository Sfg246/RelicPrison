package site.mcrelicworld.relicprison.statistics;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class MiningStatisticsDimensions {
    private MiningStatisticsDimensions() { }

    static Map<Key, Long> committed(String mineId, Map<Material, Integer> materials,
                                    Map<String, Integer> customBlocks, boolean bulk, long autoSellBlocks,
                                    long autoPickupBlocks, long autoBlockBlocks) {
        return committed(mineId, materials, customBlocks, bulk, autoSellBlocks, autoPickupBlocks,
                autoBlockBlocks, 0L);
    }

    static Map<Key, Long> committed(String mineId, Map<Material, Integer> materials,
                                    Map<String, Integer> customBlocks, boolean bulk, long autoSellBlocks,
                                    long autoPickupBlocks, long autoBlockBlocks, long fallbackDroppedItems) {
        long total = materials.values().stream().filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue).filter(value -> value > 0).sum();
        Map<Key, Long> dimensions = new HashMap<>();
        dimensions.merge(new Key("mine", mineId.toLowerCase(Locale.ROOT)), total, Long::sum);
        dimensions.merge(new Key("source", bulk ? "bulk" : "normal"), total, Long::sum);
        dimensions.merge(new Key("total", "blocks"), total, Long::sum);
        if (autoSellBlocks > 0) dimensions.merge(new Key("flag", "autosell"), autoSellBlocks, Long::sum);
        if (autoPickupBlocks > 0) dimensions.merge(new Key("flag", "autopickup"), autoPickupBlocks, Long::sum);
        if (autoBlockBlocks > 0) dimensions.merge(new Key("flag", "autoblock"), autoBlockBlocks, Long::sum);
        if (fallbackDroppedItems > 0) {
            dimensions.merge(new Key("delivery", "fallback_dropped_items"), fallbackDroppedItems, Long::sum);
        }
        for (var entry : materials.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                dimensions.merge(new Key("material", entry.getKey().name().toLowerCase(Locale.ROOT)),
                        entry.getValue().longValue(), Long::sum);
            }
        }
        if (customBlocks != null) {
            for (var entry : customBlocks.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                    dimensions.merge(new Key("custom_block", entry.getKey().toLowerCase(Locale.ROOT)),
                            entry.getValue().longValue(), Long::sum);
                }
            }
        }
        return Map.copyOf(dimensions);
    }

    record Key(String type, String key) { }
}
