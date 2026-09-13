package site.mcrelicworld.relicprison.mining;

import org.bukkit.Material;

import java.util.Map;
import java.util.Set;

public record MiningConfig(
        boolean playerToggleAutoPickup,
        boolean playerToggleAutoSmelt,
        boolean playerToggleAutoBlock,
        OverflowMode overflowMode,
        int sellSummarySeconds,
        int maximumBlocksPerOperation,
        int maximumOperationsPerPlayerPerSecond,
        int maximumGlobalBlocksPerTick,
        Map<Material, Material> smeltConversions,
        Map<Material, Material> blockConversions,
        Set<Material> fortuneExcluded,
        double fortuneMultiplier,
        int maximumFortuneLevel,
        int minimumToolDurability,
        int lowDurabilityWarning,
        BulkDurabilityMode bulkDurabilityMode,
        int bulkDurabilityCap,
        Map<Material, Integer> experienceByMaterial,
        Map<String, Integer> experienceByMine,
        Map<String, Double> rankExperienceMultipliers,
        Map<String, Double> prestigeExperienceMultipliers,
        Map<String, Double> permissionExperienceMultipliers,
        double bulkExperienceScaling,
        int maximumExperiencePerOperation,
        ExperienceMultiplierMode experienceMultiplierMode,
        boolean autoBlockUseInventory
) {
    public MiningConfig {
        smeltConversions = Map.copyOf(smeltConversions);
        blockConversions = Map.copyOf(blockConversions);
        fortuneExcluded = Set.copyOf(fortuneExcluded);
        experienceByMaterial = Map.copyOf(experienceByMaterial);
        experienceByMine = Map.copyOf(experienceByMine);
        rankExperienceMultipliers = Map.copyOf(rankExperienceMultipliers);
        prestigeExperienceMultipliers = Map.copyOf(prestigeExperienceMultipliers);
        permissionExperienceMultipliers = Map.copyOf(permissionExperienceMultipliers);
        if (sellSummarySeconds < 1 || maximumBlocksPerOperation < 1 || maximumGlobalBlocksPerTick < 1) {
            throw new IllegalArgumentException("Mining limits must be positive");
        }
        if (fortuneMultiplier < 0 || maximumFortuneLevel < 0 || minimumToolDurability < 0 || lowDurabilityWarning < 0
                || bulkExperienceScaling < 0 || maximumExperiencePerOperation < 0) {
            throw new IllegalArgumentException("Mining numeric values cannot be negative");
        }
    }

    public enum OverflowMode { DROP, SELL, CANCEL }
    public enum BulkDurabilityMode { ONE_PER_OPERATION, PER_BLOCK, CAPPED }
    public enum ExperienceMultiplierMode { MULTIPLICATIVE, ADDITIVE_BONUSES }
}
