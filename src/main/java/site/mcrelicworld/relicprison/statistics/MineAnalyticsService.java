package site.mcrelicworld.relicprison.statistics;

import site.mcrelicworld.relicprison.RelicPrisonPlugin;

/** Gate and boundary for mine-level analytics, separate from player mining statistics. */
public final class MineAnalyticsService {
    private final RelicPrisonPlugin plugin;

    public MineAnalyticsService(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.config().snapshot().features().mineAnalytics();
    }
}
