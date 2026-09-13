package site.mcrelicworld.relicprison.selection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.model.BlockPosition;
import site.mcrelicworld.relicprison.config.SelectionConfig;
import site.mcrelicworld.relicprison.integration.WorldEditSelectionProvider;
import site.mcrelicworld.relicprison.mine.Cuboid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SelectionManager {
    private final RelicPrisonPlugin plugin;
    private final WorldEditSelectionProvider worldEdit;
    private final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> previewTasks = new ConcurrentHashMap<>();

    public SelectionManager(RelicPrisonPlugin plugin, WorldEditSelectionProvider worldEdit) {
        this.plugin = plugin;
        this.worldEdit = worldEdit;
    }

    public SelectionSession session(Player player) { return sessions.computeIfAbsent(player.getUniqueId(), SelectionSession::new); }

    public void setFirst(Player player, Location location) { session(player).first(location); }
    public void setSecond(Player player, Location location) { session(player).second(location); }

    public Optional<ResolvedSelection> resolve(Player player) {
        SelectionSession internal = session(player);
        if (internal.complete()) {
            World world = internal.first().getWorld();
            return Optional.of(new ResolvedSelection(world, internal.cuboid()));
        }
        if (plugin.config().snapshot().features().worldEditFawe()) {
            return worldEdit.selection(player).map(value -> new ResolvedSelection(value.world(), value.cuboid()));
        }
        return Optional.empty();
    }

    public void beginPreview(Player player, SelectionMode mode, String mineId, World world, Cuboid bounds) {
        beginPreview(player, mode, mineId, null, null, world, bounds);
    }

    public void beginPreview(Player player, SelectionMode mode, String mineId, String sourceMineId, String displayName,
                             World world, Cuboid bounds) {
        validate(player, mode, mineId, sourceMineId, world, bounds);
        cancelPreview(player.getUniqueId(), false);
        SelectionConfig config = plugin.config().snapshot().selection();
        long expires = System.currentTimeMillis() + config.timeoutSeconds() * 1000L;
        session(player).pending(new SelectionSession.PendingAction(mode, mineId, sourceMineId, displayName,
                world.getUID(), world.getName(), bounds, expires));
        List<BlockPosition> points = outline(bounds, config.previewSpacing(), config.previewMaxParticles());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> preview(player.getUniqueId(), points), 0L,
                config.previewRefreshTicks());
        previewTasks.put(player.getUniqueId(), task);
    }

    private void validate(Player player, SelectionMode mode, String mineId, String sourceMineId, World world, Cuboid bounds) {
        if (world == null) throw new IllegalArgumentException("Destination world is not loaded");
        if (bounds.minimum().y() < world.getMinHeight() || bounds.maximum().y() >= world.getMaxHeight()) {
            throw new IllegalArgumentException("Selection Y range must stay between " + world.getMinHeight()
                    + " and " + (world.getMaxHeight() - 1));
        }
        SelectionConfig config = plugin.config().snapshot().selection();
        if (bounds.volume() > config.maximumVolume()) {
            throw new IllegalArgumentException("Selection volume " + bounds.volume() + " exceeds limit " + config.maximumVolume());
        }
        if (Math.abs(bounds.minimum().x()) > 29_999_984 || Math.abs(bounds.maximum().x()) > 29_999_984
                || Math.abs(bounds.minimum().z()) > 29_999_984 || Math.abs(bounds.maximum().z()) > 29_999_984) {
            throw new IllegalArgumentException("Selection is outside the safe world coordinate range");
        }
        String lockCandidate = sourceMineId == null ? mineId : sourceMineId;
        if (plugin.mineStructures() != null && plugin.mineStructures().isLocked(lockCandidate)) {
            throw new IllegalArgumentException("Mine " + lockCandidate + " is locked by an incomplete structure operation");
        }
        if (plugin.mineStructures() != null && plugin.mineStructures().isLocked(mineId)) {
            throw new IllegalArgumentException("Mine " + mineId + " is locked by an incomplete structure operation");
        }
        if (plugin.mineStructures() != null
                && plugin.mineStructures().intersectsLockedRegion(world.getUID(), bounds)) {
            throw new IllegalArgumentException("Selection intersects a region locked by an incomplete structure operation");
        }
        if ((mode == SelectionMode.MOVE || mode == SelectionMode.COPY) && sourceMineId != null
                && plugin.mineResets() != null && plugin.mineResets().isResetting(sourceMineId)) {
            throw new IllegalArgumentException("Mine structure operations cannot run during an active reset");
        }
        if ((mode == SelectionMode.MOVE || mode == SelectionMode.COPY) && sourceMineId != null) {
            plugin.mineService().findMine(sourceMineId).ifPresent(source -> {
                if (source.worldId().equals(world.getUID()) && source.bounds().intersects(bounds)) {
                    throw new IllegalArgumentException("Structure source and destination cannot overlap");
                }
                if (source.bounds().volume() != bounds.volume()
                        || source.bounds().maximum().x() - source.bounds().minimum().x()
                        != bounds.maximum().x() - bounds.minimum().x()
                        || source.bounds().maximum().y() - source.bounds().minimum().y()
                        != bounds.maximum().y() - bounds.minimum().y()
                        || source.bounds().maximum().z() - source.bounds().minimum().z()
                        != bounds.maximum().z() - bounds.minimum().z()) {
                    throw new IllegalArgumentException("Structure destination must match the current mine dimensions");
                }
            });
        }
        if (!config.allowOverlap() && mode != SelectionMode.DELETE) {
            String ignored = mode == SelectionMode.RESIZE || mode == SelectionMode.MOVE ? mineId : null;
            for (site.mcrelicworld.relicprison.mine.MineDefinition existing : plugin.mineService().mines()) {
                if (ignored != null && existing.id().equalsIgnoreCase(ignored)) continue;
                if (existing.worldId().equals(world.getUID()) && existing.bounds().intersects(bounds)) {
                    throw new IllegalArgumentException("Selection overlaps existing mine " + existing.id());
                }
            }
        }
    }

    private void preview(UUID ownerId, List<BlockPosition> points) {
        Player owner = Bukkit.getPlayer(ownerId);
        SelectionSession current = sessions.get(ownerId);
        if (owner == null || current == null || current.pending() == null) {
            cancelPreview(ownerId, false);
            return;
        }
        if (System.currentTimeMillis() >= current.pending().expiresAt()) {
            plugin.messages().send(owner, "selection-expired");
            cancelPreview(ownerId, true);
            return;
        }
        World currentWorld = Bukkit.getWorld(current.pending().worldId());
        if (currentWorld == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().getUID().equals(currentWorld.getUID())) continue;
            if (!viewer.getUniqueId().equals(ownerId) && !viewer.hasPermission("relicprison.admin.mine.preview")) continue;
            for (BlockPosition point : points) {
                viewer.spawnParticle(Particle.END_ROD, point.x() + 0.5, point.y() + 0.5, point.z() + 0.5, 1, 0, 0, 0, 0);
            }
        }
    }

    public SelectionSession.PendingAction pending(Player player) {
        SelectionSession current = sessions.get(player.getUniqueId());
        if (current == null || current.pending() == null) return null;
        if (System.currentTimeMillis() >= current.pending().expiresAt()) {
            cancelPreview(player.getUniqueId(), true);
            return null;
        }
        return current.pending();
    }

    public void clearPending(Player player) { cancelPreview(player.getUniqueId(), true); }

    public void remove(Player player) {
        cancelPreview(player.getUniqueId(), true);
        sessions.remove(player.getUniqueId());
    }

    public void shutdown() {
        for (UUID playerId : List.copyOf(previewTasks.keySet())) cancelPreview(playerId, false);
        sessions.clear();
    }

    private void cancelPreview(UUID playerId, boolean clearPending) {
        BukkitTask task = previewTasks.remove(playerId);
        if (task != null) task.cancel();
        if (clearPending) {
            SelectionSession session = sessions.get(playerId);
            if (session != null) session.pending(null);
        }
    }

    private static List<BlockPosition> outline(Cuboid cuboid, int spacing, int cap) {
        BlockPosition min = cuboid.minimum();
        BlockPosition max = cuboid.maximum();
        long edgeLength = 4L * ((long) max.x() - min.x() + (long) max.y() - min.y() + (long) max.z() - min.z() + 3L);
        int effectiveSpacing = Math.max(spacing, (int) Math.max(1L, (edgeLength + cap - 1L) / cap));
        Set<BlockPosition> points = new LinkedHashSet<>(Math.min(cap * 2, 4096));
        addLine(points, min.x(), min.y(), min.z(), max.x(), min.y(), min.z(), effectiveSpacing);
        addLine(points, min.x(), max.y(), min.z(), max.x(), max.y(), min.z(), effectiveSpacing);
        addLine(points, min.x(), min.y(), max.z(), max.x(), min.y(), max.z(), effectiveSpacing);
        addLine(points, min.x(), max.y(), max.z(), max.x(), max.y(), max.z(), effectiveSpacing);
        addLine(points, min.x(), min.y(), min.z(), min.x(), max.y(), min.z(), effectiveSpacing);
        addLine(points, max.x(), min.y(), min.z(), max.x(), max.y(), min.z(), effectiveSpacing);
        addLine(points, min.x(), min.y(), max.z(), min.x(), max.y(), max.z(), effectiveSpacing);
        addLine(points, max.x(), min.y(), max.z(), max.x(), max.y(), max.z(), effectiveSpacing);
        addLine(points, min.x(), min.y(), min.z(), min.x(), min.y(), max.z(), effectiveSpacing);
        addLine(points, max.x(), min.y(), min.z(), max.x(), min.y(), max.z(), effectiveSpacing);
        addLine(points, min.x(), max.y(), min.z(), min.x(), max.y(), max.z(), effectiveSpacing);
        addLine(points, max.x(), max.y(), min.z(), max.x(), max.y(), max.z(), effectiveSpacing);
        List<BlockPosition> all = new ArrayList<>(points);
        if (all.size() <= cap) return all;
        List<BlockPosition> sampled = new ArrayList<>(cap);
        double stride = (double) all.size() / cap;
        for (int i = 0; i < cap; i++) sampled.add(all.get((int) Math.floor(i * stride)));
        return sampled;
    }

    private static void addLine(Set<BlockPosition> points, int x1, int y1, int z1, int x2, int y2, int z2, int spacing) {
        int dx = Integer.compare(x2, x1), dy = Integer.compare(y2, y1), dz = Integer.compare(z2, z1);
        int length = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
        for (int i = 0; i <= length; i += Math.max(1, spacing)) points.add(new BlockPosition(x1 + dx * i, y1 + dy * i, z1 + dz * i));
        points.add(new BlockPosition(x2, y2, z2));
    }

    public record ResolvedSelection(World world, Cuboid cuboid) {}
}
