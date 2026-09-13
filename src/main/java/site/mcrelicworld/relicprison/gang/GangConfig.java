package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GangConfig(
        boolean enabled,
        BigDecimal creationCost,
        String namePattern,
        int nameMinimum,
        int nameMaximum,
        String tagPattern,
        int tagMinimum,
        int tagMaximum,
        int defaultMemberLimit,
        int maximumMemberLimit,
        Map<String, Integer> memberPermissionLimits,
        Duration inviteTimeout,
        BigDecimal bankCapacity,
        BigDecimal dailyWithdrawalLimit,
        int maximumLevel,
        BigDecimal levelBaseXp,
        BigDecimal levelGrowth,
        Map<ContributionType, BigDecimal> xpSources,
        List<DefaultRank> defaultRanks,
        Map<String, UpgradeDefinition> upgrades,
        Map<String, MissionDefinition> missions,
        BigDecimal maximumBoosterMultiplier,
        Duration maximumBoosterDuration,
        List<String> leaderboardCategories,
        boolean seasonsEnabled,
        String seasonRewardPlan,
        String unavailablePlaceholder
) {
    public GangConfig {
        xpSources = Map.copyOf(xpSources);
        memberPermissionLimits = Map.copyOf(memberPermissionLimits);
        defaultRanks = List.copyOf(defaultRanks);
        upgrades = Map.copyOf(upgrades);
        missions = Map.copyOf(missions);
        leaderboardCategories = List.copyOf(leaderboardCategories);
    }

    public BigDecimal requiredXp(int level) {
        if (level >= maximumLevel) return BigDecimal.ZERO;
        return levelBaseXp.multiply(levelGrowth.pow(Math.max(0, level - 1)));
    }

    public enum ContributionType { BLOCKS, MONEY, RANKUPS, PRESTIGES, BLOCK_EVENTS, MISSIONS, LEADERBOARD }
    public enum CostType { MONEY, POINTS }
    public enum MissionObjective { BLOCKS, MATERIAL_BLOCKS, MONEY, BLOCK_EVENTS, RANKUPS, PRESTIGES }
    public enum ResetPeriod { DAILY, WEEKLY, MONTHLY, SEASON }

    public record DefaultRank(String key, String displayName, int priority, String color,
                              Set<GangPermission> permissions) {
        public DefaultRank { permissions = Set.copyOf(permissions); }
    }

    public record UpgradeDefinition(String id, String displayName, CostType costType,
                                    List<UpgradeTier> tiers, List<UpgradePrerequisite> prerequisites) {
        public UpgradeDefinition {
            tiers = List.copyOf(tiers);
            prerequisites = List.copyOf(prerequisites);
        }
        public int maxTier() { return tiers.size(); }
        public UpgradeTier tier(int tier) {
            if (tier <= 0 || tier > tiers.size()) throw new IllegalArgumentException("Invalid upgrade tier: " + tier);
            return tiers.get(tier - 1);
        }
    }

    public record UpgradeTier(BigDecimal cost, Map<String, BigDecimal> effects) {
        public UpgradeTier { effects = Map.copyOf(effects); }
    }

    public record UpgradePrerequisite(String upgradeId, int tier) { }

    public record MissionDefinition(String id, String displayName, MissionObjective objective,
                                    String targetKey, BigDecimal target, ResetPeriod resetPeriod,
                                    MissionReward reward) { }

    public record MissionReward(BigDecimal gangXp, long gangPoints, BigDecimal gangMoney,
                                GangBooster.Type boosterType, BigDecimal boosterMultiplier,
                                Duration boosterDuration, List<String> rewardComponents) {
        public MissionReward { rewardComponents = List.copyOf(rewardComponents); }
    }
}
