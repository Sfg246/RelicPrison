package site.mcrelicworld.relicprison.statistics;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class MiningStatisticsDimensionsTest {
    @Test
    void vanillaMiningRecordsMaterialAndNormalWithoutEnabledRouteFlags() {
        Map<MiningStatisticsDimensions.Key, Long> result = MiningStatisticsDimensions.committed("Mine-A",
                Map.of(Material.STONE, 1), Map.of(), false, 0, 0, 0);

        assertEquals(1L, result.get(new MiningStatisticsDimensions.Key("material", "stone")));
        assertEquals(1L, result.get(new MiningStatisticsDimensions.Key("source", "normal")));
        assertFalse(result.containsKey(new MiningStatisticsDimensions.Key("flag", "autosell")));
        assertFalse(result.containsKey(new MiningStatisticsDimensions.Key("flag", "autopickup")));
    }

    @Test
    void bulkMiningUsesActualCommittedRouteCountsAndCustomIdentity() {
        Map<MiningStatisticsDimensions.Key, Long> result = MiningStatisticsDimensions.committed("Mine-A",
                Map.of(Material.STONE, 3, Material.DIAMOND_ORE, 2), Map.of("relic:ore", 2), true,
                2, 1, 4);

        assertEquals(5L, result.get(new MiningStatisticsDimensions.Key("source", "bulk")));
        assertEquals(2L, result.get(new MiningStatisticsDimensions.Key("flag", "autosell")));
        assertEquals(1L, result.get(new MiningStatisticsDimensions.Key("flag", "autopickup")));
        assertEquals(4L, result.get(new MiningStatisticsDimensions.Key("flag", "autoblock")));
        assertEquals(2L, result.get(new MiningStatisticsDimensions.Key("custom_block", "relic:ore")));
    }
}
