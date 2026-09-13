package site.mcrelicworld.relicprison.config;

public record IntegrationConfig(
        boolean vaultEnabled,
        boolean requireEconomyProvider,
        boolean luckPermsEnabled,
        boolean worldGuardEnabled,
        boolean worldEditFaweEnabled,
        boolean itemsAdderEnabled,
        boolean advancedEnchantmentsEnabled,
        boolean placeholderApiEnabled,
        boolean geyserFloodgateAwarenessEnabled,
        String combatProvider,
        boolean combatTeleportRestrictionEnabled,
        String combatTeleportBypassPermission,
        String combatTeleportDenyMessage,
        WorldGuardConfig worldGuard
) {
    public static IntegrationConfig defaults() {
        return new IntegrationConfig(true, true, true, true, true, false, false, true, true,
                "none", false, "relicprison.bypass.combat-teleport",
                "&cYou cannot teleport to mines while combat-tagged.",
                new WorldGuardConfig("relicmine_", true, true, true, true, true, true, true, true));
    }
}
