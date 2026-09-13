package site.mcrelicworld.relicprison.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.event.RelicPlayerDataLoadEvent;

public final class ProgressionJoinListener implements Listener {
    private final RelicPrisonPlugin plugin;
    public ProgressionJoinListener(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @EventHandler public void onProfileLoaded(RelicPlayerDataLoadEvent event) {
        Player player = Bukkit.getPlayer(event.profile().uuid());
        if (player == null) return;
        if (!plugin.isReady()) return;
        boolean firstJoin = Math.abs(event.profile().lastJoin() - event.profile().firstJoin()) <= 2000;
        plugin.boosterService().playerJoin(player.getUniqueId())
                .thenCompose(ignored -> plugin.boosterService().permanentMultiplier(player.getUniqueId()))
                .thenRun(() -> plugin.multiplierService().invalidate(player.getUniqueId()))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Unable to resume boosters for " + player.getName() + ": " + rootMessage(error));
                    return null;
                });
        if (plugin.rewardLedger() != null) {
            plugin.rewardLedger().deliverPending(player.getUniqueId()).exceptionally(error -> {
                plugin.getLogger().warning("Unable to resume pending reward components for " + player.getName()
                        + ": " + rootMessage(error));
                return null;
            });
        }
        if (plugin.config().snapshot().progression().repairOnJoin()) {
            plugin.progression().recoverPending(player.getUniqueId(), "join")
                    .thenCompose(ignored -> plugin.progression().repair(player.getUniqueId(), "join", true))
                    .exceptionally(error -> {
                plugin.getLogger().severe("Progression repair failed for " + player.getName() + ": " + rootMessage(error));
                return null;
            });
        }
        if (firstJoin) {
            Bukkit.getScheduler().runTask(plugin, () -> runFirstJoinCommands(player, event.profile().currentRank()));
            if (plugin.config().snapshot().teleport().teleportFirstJoinToMine()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) plugin.mineTeleports().teleport(player, null);
                }, 20L);
            }
        }
    }


    private void runFirstJoinCommands(Player player, String rank) {
        for (String configured : plugin.config().snapshot().teleport().firstJoinCommands()) {
            String command = configured.replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%rank%", rank == null ? "" : rank);
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    plugin.getLogger().warning("First-join command returned false for " + player.getName() + ": " + command);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("First-join command failed for " + player.getName() + ": " + rootMessage(ex));
            }
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }
}
