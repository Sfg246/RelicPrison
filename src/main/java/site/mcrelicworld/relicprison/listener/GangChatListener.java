package site.mcrelicworld.relicprison.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

public final class GangChatListener implements Listener {
    private final RelicPrisonPlugin plugin;

    public GangChatListener(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (plugin.gangs() == null || !plugin.gangs().config().enabled()
                || plugin.gangs().cachedMembership(event.getPlayer().getUniqueId())
                .filter(member -> member.chatEnabled()).isEmpty()) return;
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.gangs().sendChat(event.getPlayer(), message));
    }
}
