package site.mcrelicworld.relicprison.mine;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import site.mcrelicworld.relicprison.api.event.RelicMineCreateEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineCreatedEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineDeleteEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineDeletedEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineUpdateEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineUpdatedEvent;

import site.mcrelicworld.relicprison.api.MineService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class MineServiceImpl implements MineService {
    private final JavaPlugin plugin;
    private final MineRepository repository;
    private final Map<String, MineDefinition> mines = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile MineSpatialIndex spatialIndex = MineSpatialIndex.build(List.of());

    public MineServiceImpl(JavaPlugin plugin, MineRepository repository) { this.plugin = plugin; this.repository = repository; }

    public Map<String, MineDefinition> previewLoad() throws Exception { return Map.copyOf(repository.load()); }
    public Map<String, MineDefinition> snapshot() { return Map.copyOf(mines); }

    public void applyLoaded(Map<String, MineDefinition> loaded) {
        lock.writeLock().lock();
        try {
            mines.clear();
            mines.putAll(loaded);
            spatialIndex = MineSpatialIndex.build(mines.values());
        } finally { lock.writeLock().unlock(); }
    }

    public void load() throws Exception { applyLoaded(previewLoad()); }

    @Override public Optional<MineDefinition> findMine(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(mines.get(id.toLowerCase(Locale.ROOT)));
    }

    @Override public Collection<MineDefinition> mines() {
        lock.readLock().lock();
        try {
            List<MineDefinition> copy = new ArrayList<>(mines.values());
            copy.sort(Comparator.comparingInt(MineDefinition::sortOrder).thenComparing(MineDefinition::id));
            return List.copyOf(copy);
        } finally { lock.readLock().unlock(); }
    }

    @Override public Optional<MineDefinition> mineAt(String worldName, int x, int y, int z) {
        if (worldName == null) return Optional.empty();
        lock.readLock().lock();
        try {
            return mines.values().stream()
                    .filter(MineDefinition::enabled)
                    .filter(mine -> mine.worldName().equalsIgnoreCase(worldName))
                    .filter(mine -> mine.bounds().contains(x, y, z))
                    .min(Comparator.comparingLong(MineDefinition::volume));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override public Optional<MineDefinition> mineAt(java.util.UUID worldId, int x, int y, int z) {
        if (worldId == null) return Optional.empty();
        return spatialIndex.find(worldId, x, y, z);
    }

    public void create(MineDefinition mine) throws Exception {
        lock.writeLock().lock();
        try {
            if (mines.containsKey(mine.id())) throw new IllegalArgumentException("Mine already exists: " + mine.id());
            requireNoOverlap(mine, null);
            RelicMineCreateEvent event = new RelicMineCreateEvent(mine);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) throw new IllegalStateException("Mine creation was cancelled by another plugin");
            mines.put(mine.id(), mine);
            saveOrRollback(mine.id(), null);
            spatialIndex = MineSpatialIndex.build(mines.values());
            Bukkit.getPluginManager().callEvent(new RelicMineCreatedEvent(mine));
        } finally { lock.writeLock().unlock(); }
    }

    public void update(MineDefinition mine) throws Exception {
        lock.writeLock().lock();
        try {
            MineDefinition previous = mines.get(mine.id());
            if (previous == null) throw new IllegalArgumentException("Mine does not exist: " + mine.id());
            requireNoOverlap(mine, mine.id());
            RelicMineUpdateEvent event = new RelicMineUpdateEvent(previous, mine);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) throw new IllegalStateException("Mine update was cancelled by another plugin");
            mines.put(mine.id(), mine);
            saveOrRollback(mine.id(), previous);
            spatialIndex = MineSpatialIndex.build(mines.values());
            Bukkit.getPluginManager().callEvent(new RelicMineUpdatedEvent(previous, mine));
        } finally { lock.writeLock().unlock(); }
    }


    public void rename(String oldId, String newId, String newDisplayName) throws Exception {
        String oldNormalized = MineDefinition.normalizeId(oldId);
        String newNormalized = MineDefinition.normalizeId(newId);
        lock.writeLock().lock();
        try {
            MineDefinition previous = mines.get(oldNormalized);
            if (previous == null) throw new IllegalArgumentException("Mine does not exist: " + oldNormalized);
            if (!oldNormalized.equals(newNormalized) && mines.containsKey(newNormalized)) {
                throw new IllegalArgumentException("Mine already exists: " + newNormalized);
            }
            MineDefinition renamed = previous.withIdentity(newNormalized, newDisplayName);
            RelicMineUpdateEvent event = new RelicMineUpdateEvent(previous, renamed);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) throw new IllegalStateException("Mine rename was cancelled by another plugin");
            mines.remove(oldNormalized);
            mines.put(newNormalized, renamed);
            try {
                repository.save(mines.values());
                spatialIndex = MineSpatialIndex.build(mines.values());
                Bukkit.getPluginManager().callEvent(new RelicMineUpdatedEvent(previous, renamed));
            } catch (Exception ex) {
                mines.remove(newNormalized);
                mines.put(oldNormalized, previous);
                throw ex;
            }
        } finally { lock.writeLock().unlock(); }
    }

    public void delete(String id) throws Exception {
        String normalized = MineDefinition.normalizeId(id);
        lock.writeLock().lock();
        try {
            MineDefinition removed = mines.get(normalized);
            if (removed == null) throw new IllegalArgumentException("Mine does not exist: " + normalized);
            RelicMineDeleteEvent event = new RelicMineDeleteEvent(removed);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) throw new IllegalStateException("Mine deletion was cancelled by another plugin");
            mines.remove(normalized);
            try {
                repository.save(mines.values());
                spatialIndex = MineSpatialIndex.build(mines.values());
                Bukkit.getPluginManager().callEvent(new RelicMineDeletedEvent(removed));
            } catch (Exception ex) { mines.put(normalized, removed); throw ex; }
        } finally { lock.writeLock().unlock(); }
    }


    private void requireNoOverlap(MineDefinition candidate, String ignoredMineId) {
        for (MineDefinition existing : mines.values()) {
            if (ignoredMineId != null && existing.id().equals(ignoredMineId)) continue;
            if (!existing.worldId().equals(candidate.worldId())) continue;
            if (existing.bounds().intersects(candidate.bounds())) {
                throw new IllegalArgumentException("Mine " + candidate.id() + " overlaps existing mine " + existing.id());
            }
        }
    }

    private void saveOrRollback(String id, MineDefinition previous) throws Exception {
        try { repository.save(mines.values()); }
        catch (Exception ex) {
            if (previous == null) mines.remove(id); else mines.put(id, previous);
            throw ex;
        }
    }
}
