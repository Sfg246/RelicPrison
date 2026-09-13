package site.mcrelicworld.relicprison.listener;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.access.MineAccessServiceImpl;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.reset.MineResetServiceImpl;
import site.mcrelicworld.relicprison.teleport.MineTeleportService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MineGameplayListener implements Listener {
    private final RelicPrisonPlugin plugin;
    private final MineAccessServiceImpl access;
    private final MineResetServiceImpl resets;
    private final MineTeleportService teleports;
    private final Map<UUID, Long> accessMessageCooldown = new ConcurrentHashMap<>();

    public MineGameplayListener(RelicPrisonPlugin plugin, MineAccessServiceImpl access,
                                MineResetServiceImpl resets, MineTeleportService teleports) {
        this.plugin = plugin;
        this.access = access;
        this.resets = resets;
        this.teleports = teleports;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreakAccess(BlockBreakEvent event) {
        if (!plugin.isReady() || !plugin.ensureProfileReady(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.config().snapshot().isExcludedWorld(event.getBlock().getWorld().getName())) return;
        MineDefinition mine = plugin.mineService().mineAt(event.getBlock().getWorld().getUID(),
                event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()).orElse(null);
        if (mine == null) return;
        Player player = event.getPlayer();
        if (resets.isResetting(mine.id())) {
            event.setCancelled(true);
            throttled(player, "mine-reset-in-progress", Map.of("mine", mine.displayName()));
            return;
        }
        if (!access.canMine(player.getUniqueId(), mine.id())) {
            event.setCancelled(true);
            throttled(player, "mine-locked", Map.of("mine", mine.displayName()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.isReady()) {
            event.setCancelled(true);
            plugin.sendDataLoading(event.getPlayer());
            return;
        }
        if (plugin.config().snapshot().isExcludedWorld(event.getBlock().getWorld().getName())) return;
        MineDefinition mine = plugin.mineService().mineAt(event.getBlock().getWorld().getUID(),
                event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()).orElse(null);
        if (mine != null && !event.getPlayer().hasPermission("relicprison.bypass.build")) {
            event.setCancelled(true);
            throttled(event.getPlayer(), "mine-build-denied", Map.of("mine", mine.displayName()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.isReady()) return;
        Location to = event.getTo();
        if (to == null) return;
        teleports.moved(event.getPlayer(), to);
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()
                && to.getWorld() != null && from.getWorld() != null && to.getWorld().getUID().equals(from.getWorld().getUID())) return;
        if (!plugin.config().snapshot().teleport().enforceMineEntry() || to.getWorld() == null
                || plugin.config().snapshot().isExcludedWorld(to.getWorld().getName())) return;
        MineDefinition mine = plugin.mineService().mineAt(to.getWorld().getUID(), to.getBlockX(), to.getBlockY(), to.getBlockZ()).orElse(null);
        if (mine != null && !access.canEnter(event.getPlayer().getUniqueId(), mine.id())) {
            event.setTo(from);
            throttled(event.getPlayer(), "mine-locked", Map.of("mine", mine.displayName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity damagedEntity = event.getEntity();
        if (damagedEntity instanceof Player player && plugin.config().snapshot().teleport().cancelOnDamage()) {
            teleports.cancel(player.getUniqueId(), true);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        teleports.cancel(event.getPlayer().getUniqueId(), false);
        accessMessageCooldown.remove(event.getPlayer().getUniqueId());
    }

    private void throttled(Player player, String message, Map<String, String> placeholders) {
        long now = System.currentTimeMillis();
        Long previous = accessMessageCooldown.put(player.getUniqueId(), now);
        if (previous == null || now - previous >= 1500) plugin.messages().send(player, message, placeholders);
    }
}
