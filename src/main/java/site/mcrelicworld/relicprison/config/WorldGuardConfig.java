package site.mcrelicworld.relicprison.config;

public record WorldGuardConfig(
        String regionPrefix,
        boolean createRegions,
        boolean updateRegions,
        boolean deleteRegions,
        boolean denyBlockPlace,
        boolean allowBlockBreak,
        boolean denyExplosions,
        boolean denyFireSpread,
        boolean denyFluidFlow
) {}
