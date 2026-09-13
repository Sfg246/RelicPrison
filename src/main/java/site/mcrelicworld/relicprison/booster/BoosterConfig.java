package site.mcrelicworld.relicprison.booster;

import org.bukkit.Material;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record BoosterConfig(
        StackingMode stackingMode,
        BigDecimal maximumFinalMultiplier,
        BigDecimal maximumBoosterMultiplier,
        Duration maximumDuration,
        boolean offlineTimeContinues,
        boolean strengthStacks,
        boolean durationStacks,
        Material itemMaterial,
        String itemName,
        List<String> itemLore,
        int customModelData,
        Map<String, BigDecimal> permissionMultipliers
) {
    public BoosterConfig {
        if (maximumFinalMultiplier.signum() <= 0 || maximumBoosterMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("Multiplier limits must be positive");
        }
        if (maximumDuration.isZero() || maximumDuration.isNegative()) {
            throw new IllegalArgumentException("Maximum booster duration must be positive");
        }
        itemLore = List.copyOf(itemLore);
        permissionMultipliers = Map.copyOf(permissionMultipliers);
    }

    public enum StackingMode {
        MULTIPLY_ALL,
        ADD_BONUSES,
        HIGHEST_ONLY,
        HIGHEST_PER_CATEGORY
    }
}
