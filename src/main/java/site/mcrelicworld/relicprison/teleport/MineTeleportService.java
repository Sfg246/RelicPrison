package site.mcrelicworld.relicprison.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.access.MineAccessServiceImpl;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.mine.MineServiceImpl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MineTeleportService {
    private final RelicPrisonPlugin plugin;
    private final MineServiceImpl mines;
    private final MineAccessServiceImpl access;
    private final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();

    public MineTeleportService(RelicPrisonPlugin plugin, MineServiceImpl mines, MineAccessServiceImpl access) {
        this.plugin = plugin;
        this.mines = mines;
        this.access = access;
    }

    public boolean teleport(Player player, String requestedMineId) {
        MineDefinition mine = requestedMineId == null
                ? access.currentRankMine(player.getUniqueId()).orElse(null)
                : mines.findMine(requestedMineId).orElse(null);
        if (mine == null) {
            plugin.messages().send(player, "mine-not-found", Map.of("mine", requestedMineId == null ? "current" : requestedMineId));
            return false;
        }
        if (!access.canEnter(player.getUniqueId(), mine.id())) {
            plugin.messages().send(player, "mine-locked", Map.of("mine", mine.displayName()));
            return false;
        }
        if (plugin.combatTags() != null && plugin.combatTags().blocksMineTeleport(player)) {
            plugin.messages().error(player, plugin.config().snapshot().integrations().combatTeleportDenyMessage());
            return false;
        }
        World world = Bukkit.getWorld(mine.worldId());
        if (world == null) {
            plugin.messages().send(player, "mine-world-unloaded", Map.of("mine", mine.displayName()));
            return false;
        }
        Location destination = validatedDestination(mine, world);
        int warmup = player.hasPermission("relicprison.bypass.teleport-warmup") ? 0
                : plugin.config().snapshot().teleport().warmupSeconds();
        cancel(player.getUniqueId(), false);
        if (warmup <= 0) {
            if (!player.teleport(destination)) {
                plugin.messages().send(player, "mine-teleport-failed", Map.of("mine", mine.displayName()));
                return false;
            }
            plugin.messages().send(player, "mine-teleported", Map.of("mine", mine.displayName()));
            return true;
        }
        Location start = player.getLocation().clone();
        plugin.messages().send(player, "mine-teleport-warmup", Map.of("mine", mine.displayName(), "seconds", String.valueOf(warmup)));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingTeleport current = pending.remove(player.getUniqueId());
            if (current == null) return;
            if (!player.isOnline() || !access.canEnter(player.getUniqueId(), mine.id())) return;
            if (!player.teleport(destination)) {
                plugin.messages().send(player, "mine-teleport-failed", Map.of("mine", mine.displayName()));
                return;
            }
            plugin.messages().send(player, "mine-teleported", Map.of("mine", mine.displayName()));
        }, warmup * 20L);
        pending.put(player.getUniqueId(), new PendingTeleport(task, start, mine.id()));
        return true;
    }


    private Location validatedDestination(MineDefinition mine, World world) {
        Location candidate = mine.spawn() == null ? world.getSpawnLocation() : mine.spawn().toLocation(world);
        boolean finite = Double.isFinite(candidate.getX()) && Double.isFinite(candidate.getY())
                && Double.isFinite(candidate.getZ()) && Float.isFinite(candidate.getYaw())
                && Float.isFinite(candidate.getPitch());
        boolean validHeight = candidate.getY() >= world.getMinHeight() && candidate.getY() < world.getMaxHeight();
        if (!finite || !validHeight) {
            plugin.getLogger().warning("Mine " + mine.id() + " has an invalid teleport spawn; using world spawn.");
            return world.getSpawnLocation();
        }
        return candidate;
    }

    public void cancel(UUID playerId, boolean notify) {
        PendingTeleport removed = pending.remove(playerId);
        if (removed == null) return;
        removed.task().cancel();
        Player player = Bukkit.getPlayer(playerId);
        if (notify && player != null) plugin.messages().send(player, "mine-teleport-cancelled");
    }

    public boolean moved(Player player, Location to) {
        PendingTeleport current = pending.get(player.getUniqueId());
        if (current == null || !plugin.config().snapshot().teleport().cancelOnMove()) return false;
        Location start = current.start();
        if (to.getWorld() == null || start.getWorld() == null || !to.getWorld().getUID().equals(start.getWorld().getUID())
                || to.getBlockX() != start.getBlockX() || to.getBlockY() != start.getBlockY() || to.getBlockZ() != start.getBlockZ()) {
            cancel(player.getUniqueId(), true);
            return true;
        }
        return false;
    }

    public void shutdown() {
        pending.values().forEach(value -> value.task().cancel());
        pending.clear();
    }

    private record PendingTeleport(BukkitTask task, Location start, String mineId) {}
}
