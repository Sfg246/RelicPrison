package site.mcrelicworld.relicprison.config;

public record FeatureToggles(
        boolean autoSell,
        boolean autoPickup,
        boolean autoSmelt,
        boolean autoBlock,
        boolean fortune,
        boolean customBlockDrops,
        boolean miningXp,
        boolean blockEvents,
        boolean playerLeaderboards,
        boolean playerMiningStatistics,
        boolean mineAnalytics,
        boolean worldGuard,
        boolean worldEditFawe,
        boolean itemsAdder,
        boolean advancedEnchantments,
        boolean multipleCurrencies,
        boolean resetCommandHooks,
        boolean updateChecker
) {
    public FeatureToggles withIntegrations(IntegrationConfig integrations) {
        return new FeatureToggles(autoSell, autoPickup, autoSmelt, autoBlock, fortune, customBlockDrops,
                miningXp, blockEvents, playerLeaderboards, playerMiningStatistics, mineAnalytics, integrations.worldGuardEnabled(),
                integrations.worldEditFaweEnabled(), integrations.itemsAdderEnabled(),
                integrations.advancedEnchantmentsEnabled(), multipleCurrencies, resetCommandHooks, updateChecker);
    }
}
