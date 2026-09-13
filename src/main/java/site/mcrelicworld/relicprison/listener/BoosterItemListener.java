package site.mcrelicworld.relicprison.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.booster.BoosterItemService;
import site.mcrelicworld.relicprison.booster.BoosterServiceImpl;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BoosterItemListener implements Listener {
    private final RelicPrisonPlugin plugin;
    private final BoosterItemService items;
    private final BoosterServiceImpl boosters;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public BoosterItemListener(RelicPrisonPlugin plugin, BoosterItemService items, BoosterServiceImpl boosters) {
        this.plugin = plugin; this.items = items; this.boosters = boosters;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        BoosterItemService.BoosterItem data = items.read(event.getItem()).orElse(null);
        if (data == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!pending.add(player.getUniqueId())) {
            plugin.messages().send(player, "booster-activation-pending", Map.of());
            return;
        }
        if (data.serverWide() && !player.hasPermission("relicprison.booster.activate.server")) {
            pending.remove(player.getUniqueId());
            plugin.messages().send(player, "no-permission", Map.of());
            return;
        }
        boosters.activate(data.serverWide() ? null : player.getUniqueId(), data.serverWide(), data.multiplier(),
                        data.durationMillis(), player.getUniqueId().toString())
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    pending.remove(player.getUniqueId());
                    if (error != null || result == null || !result.success()) {
                        plugin.messages().send(player, "booster-activation-failed", Map.of("error",
                                error == null ? result.message() : root(error)));
                        return;
                    }
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    BoosterItemService.BoosterItem current = items.read(hand).orElse(null);
                    if (current == null || current.serverWide() != data.serverWide()
                            || current.multiplier().compareTo(data.multiplier()) != 0
                            || current.durationMillis() != data.durationMillis()) {
                        boosters.remove(result.booster().id());
                        plugin.messages().send(player, "booster-activation-failed", Map.of("error", "Booster item moved before activation completed"));
                        return;
                    }
                    if (hand.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
                    else hand.setAmount(hand.getAmount() - 1);
                    plugin.multiplierService().invalidateAll();
                    plugin.messages().send(player, "booster-activated", Map.of(
                            "multiplier", data.multiplier().stripTrailingZeros().toPlainString(),
                            "duration", site.mcrelicworld.relicprison.util.DurationParser.format(data.durationMillis()),
                            "type", data.serverWide() ? "server" : "personal"));
                }));
    }

    private static String root(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }
}
