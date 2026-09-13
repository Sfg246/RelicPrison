package site.mcrelicworld.relicprison.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

public final class PlayerDataListener implements Listener {
    private final RelicPrisonPlugin plugin;

    public PlayerDataListener(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.statistics() != null && plugin.isReady()) plugin.statistics().playerJoin(event.getPlayer().getUniqueId());
        if (plugin.leaderboardRewards() != null && plugin.isReady()) {
            plugin.leaderboardRewards().deliverPending(event.getPlayer().getUniqueId()).exceptionally(error -> {
                plugin.getLogger().warning("Unable to deliver pending leaderboard rewards for "
                        + event.getPlayer().getName() + ": " + error.getMessage());
                return null;
            });
        }
        if (plugin.gangs() != null && plugin.isReady()) plugin.gangs().refresh(event.getPlayer().getUniqueId());
        plugin.loadProfileWhenReady(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.statistics() != null) plugin.statistics().playerQuit(event.getPlayer().getUniqueId());
        if (plugin.selections() != null) plugin.selections().remove(event.getPlayer());
        if (plugin.prisonGuis() != null) plugin.prisonGuis().clear(event.getPlayer().getUniqueId());
        if (plugin.prestigeConfirmations() != null) plugin.prestigeConfirmations().clear(event.getPlayer().getUniqueId());
        if (plugin.boosterService() != null) plugin.boosterService().playerQuit(event.getPlayer().getUniqueId()).exceptionally(error -> {
            plugin.getLogger().warning("Unable to pause personal boosters for " + event.getPlayer().getName() + ": " + error.getMessage());
            return null;
        });
        if (plugin.playerProfiles() != null) plugin.playerProfiles().unload(event.getPlayer().getUniqueId())
                .exceptionally(error -> {
                    plugin.getLogger().severe("Unable to save player profile for " + event.getPlayer().getName() + ": " + error.getMessage());
                    return null;
                });
    }
}
