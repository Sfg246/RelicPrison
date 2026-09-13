package site.mcrelicworld.relicprison.statistics;

import site.mcrelicworld.relicprison.RelicPrisonPlugin;

/** Gate and boundary for approved per-player mining statistics. */
public final class PlayerMiningStatisticsService {
    private final RelicPrisonPlugin plugin;

    public PlayerMiningStatisticsService(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.config().snapshot().features().playerMiningStatistics();
    }
}
