package site.mcrelicworld.relicprison.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

public final class PlaceholderCacheListener implements Listener {
    private final RelicPrisonPlugin plugin;

    public PlaceholderCacheListener(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) invalidate(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) invalidate(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) invalidate(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        invalidate(event.getPlayer());
    }

    private void invalidate(Player player) {
        plugin.invalidatePlaceholderCache(player.getUniqueId());
    }
}
