package site.mcrelicworld.relicprison.selection;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.util.Map;

public final class WandListener implements Listener {
    private final RelicPrisonPlugin plugin;
    private final SelectionManager selections;
    private final NamespacedKey wandKey;

    public WandListener(RelicPrisonPlugin plugin, SelectionManager selections) {
        this.plugin = plugin;
        this.selections = selections;
        this.wandKey = new NamespacedKey(plugin, "mine_selection_wand");
    }

    public NamespacedKey wandKey() { return wandKey; }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(wandKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || !isWand(event.getItem())) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Location location = event.getClickedBlock().getLocation();
        Map<String, Object> values = Map.of("x", location.getBlockX(), "y", location.getBlockY(), "z", location.getBlockZ());
        if (action == Action.LEFT_CLICK_BLOCK) {
            selections.setFirst(player, location);
            plugin.messages().send(player, "selection-point-one", values);
        } else {
            selections.setSecond(player, location);
            plugin.messages().send(player, "selection-point-two", values);
        }
    }
}
