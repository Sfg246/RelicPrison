package site.mcrelicworld.relicprison.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

public final class GangChatListener implements Listener {
    private final RelicPrisonPlugin plugin;

    public GangChatListener(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.gangs() == null || !plugin.gangs().config().enabled()
                || plugin.gangs().cachedMembership(event.getPlayer().getUniqueId())
                .filter(member -> member.chatEnabled()).isEmpty()) return;
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.gangs().sendChat(event.getPlayer(), message));
    }
}
