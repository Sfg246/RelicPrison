package site.mcrelicworld.relicprison.booster;

import org.bukkit.Bukkit;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.BoosterService;
import site.mcrelicworld.relicprison.api.event.RelicBoosterActivateEvent;
import site.mcrelicworld.relicprison.api.event.RelicBoosterExpireEvent;
import site.mcrelicworld.relicprison.api.model.BoosterView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BoosterServiceImpl implements BoosterService {
    private final RelicPrisonPlugin plugin;
    private final BoosterRepository repository;
    private final BoosterConfigRepository configs;
    private final Map<UUID, Map<String, ActiveBooster>> personal = new ConcurrentHashMap<>();
    private final Map<String, ActiveBooster> server = new ConcurrentHashMap<>();
    private final Map<String, ActiveBooster> scheduled = new ConcurrentHashMap<>();
    private final Map<String, ActiveBooster> disabled = new ConcurrentHashMap<>();
    private final Map<UUID, BigDecimal> permanentMultipliers = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pausedPlayers = ConcurrentHashMap.newKeySet();
    private int expiryTaskId = -1;

    public BoosterServiceImpl(RelicPrisonPlugin plugin, BoosterRepository repository, BoosterConfigRepository configs) {
        this.plugin = plugin; this.repository = repository; this.configs = configs;
    }

    public void initialize() {
        initializeAsync().whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().severe("Booster initialization failed: " + rootMessage(error));
                return;
            }
            startExpiryTask();
        }));
    }

    public CompletableFuture<Void> initializeAsync() {
        return reloadPersistentState();
    }

    public CompletableFuture<Void> reloadPersistentState() {
        long now = System.currentTimeMillis();
        return repository.loadPausedPlayers().thenCompose(paused -> {
            pausedPlayers.addAll(paused);
            CompletableFuture<Void> resume = CompletableFuture.completedFuture(null);
            if (configs.config().offlineTimeContinues() && !pausedPlayers.isEmpty()) {
                resume = CompletableFuture.allOf(List.copyOf(pausedPlayers).stream()
                        .map(playerId -> repository.resume(playerId, now).thenAccept(extension -> pausedPlayers.remove(playerId)))
                        .toArray(CompletableFuture[]::new));
            }
            return resume.thenCompose(ignored -> repository.loadActive(now));
        }).thenAccept(active -> {
            personal.clear();
            server.clear();
            scheduled.clear();
            disabled.clear();
            active.forEach(this::putManaged);
            if (plugin.multiplierService() != null) plugin.multiplierService().invalidateAll();
        }).orTimeout(15, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void startExpiryTask() {
        if (expiryTaskId != -1) Bukkit.getScheduler().cancelTask(expiryTaskId);
        expiryTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::expireNow, 20L, 20L);
    }

    public void reconfigure() {
        if (!configs.config().offlineTimeContinues() || pausedPlayers.isEmpty()) {
            if (plugin.multiplierService() != null) plugin.multiplierService().invalidateAll();
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID playerId : List.copyOf(pausedPlayers)) {
            repository.resume(playerId, now).thenAccept(extension -> {
                pausedPlayers.remove(playerId);
                if (extension > 0L) {
                    Map<String, ActiveBooster> values = personal.get(playerId);
                    if (values != null) values.replaceAll((id, booster) -> new ActiveBooster(booster.id(), booster.owner(),
                            booster.multiplier(), booster.expiresAt() + extension, booster.createdAt(), false, booster.activatedBy()));
                    scheduled.replaceAll((id, booster) -> !playerId.equals(booster.owner()) ? booster
                            : new ActiveBooster(booster.id(), booster.owner(), booster.multiplier(),
                            booster.expiresAt() + extension, booster.createdAt(), false, booster.activatedBy(),
                            booster.startsAt() + extension, booster.enabled()));
                }
                if (plugin.multiplierService() != null) plugin.multiplierService().invalidate(playerId);
            }).exceptionally(error -> {
                plugin.getLogger().warning("Failed to resume paused boosters for " + playerId + ": " + error.getMessage());
                return null;
            });
        }
    }

    public void shutdown() {
        if (expiryTaskId != -1) Bukkit.getScheduler().cancelTask(expiryTaskId);
    }

    public CompletableFuture<ActivationResult> activate(UUID owner, boolean serverWide, BigDecimal multiplier,
                                                         long durationMillis, String activatedBy) {
        BoosterConfig config = configs.config();
        if (multiplier.signum() <= 0 || multiplier.compareTo(config.maximumBoosterMultiplier()) > 0) {
            return CompletableFuture.completedFuture(ActivationResult.failure("Multiplier exceeds the configured limit"));
        }
        if (durationMillis <= 0 || durationMillis > config.maximumDuration().toMillis()) {
            return CompletableFuture.completedFuture(ActivationResult.failure("Duration exceeds the configured limit"));
        }
        long now = System.currentTimeMillis();
        if (!serverWide && owner == null) return CompletableFuture.completedFuture(ActivationResult.failure("Personal booster needs an owner"));

        ActiveBooster merged = merge(owner, serverWide, multiplier, durationMillis, activatedBy, now);
        RelicBoosterActivateEvent event = new RelicBoosterActivateEvent(merged.id(), owner, merged.multiplier(), merged.expiresAt());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return CompletableFuture.completedFuture(ActivationResult.failure("Booster activation was cancelled"));
        Collection<ActiveBooster> replaced = replacedBy(merged, serverWide, owner);
        return repository.save(merged).thenCompose(ignored -> {
            put(merged);
            if (replaced.isEmpty()) return CompletableFuture.completedFuture(null);
            return CompletableFuture.allOf(replaced.stream().map(repository::delete).toArray(CompletableFuture[]::new));
        }).thenApply(ignored -> {
            removeReplacedFromCache(merged, replaced);
            if (plugin.multiplierService() != null) plugin.multiplierService().invalidateAll();
            if (plugin.statistics() != null) {
                UUID statisticOwner = merged.owner();
                if (statisticOwner == null && merged.activatedBy() != null) {
                    try { statisticOwner = UUID.fromString(merged.activatedBy()); } catch (IllegalArgumentException invalidUuid) { }
                }
                if (statisticOwner != null) plugin.statistics().recordBooster(statisticOwner);
            }
            return ActivationResult.success(merged);
        });
    }

    private ActiveBooster merge(UUID owner, boolean serverWide, BigDecimal multiplier, long duration, String activatedBy, long now) {
        BoosterConfig config = configs.config();
        Collection<ActiveBooster> existing = serverWide ? server.values() : personal.getOrDefault(owner, Map.of()).values();
        ActiveBooster strongest = existing.stream().max(Comparator.comparing(ActiveBooster::multiplier)).orElse(null);
        if (strongest != null && !config.strengthStacks()) {
            BigDecimal resultMultiplier = multiplier.max(strongest.multiplier());
            long base = config.durationStacks() ? Math.max(now, strongest.expiresAt()) : now;
            long expiry = Math.min(base + duration, now + config.maximumDuration().toMillis());
            return new ActiveBooster(strongest.id(), owner, resultMultiplier, expiry, strongest.createdAt(), serverWide, activatedBy);
        }
        String id = (serverWide ? "server-" : "personal-") + UUID.randomUUID();
        return new ActiveBooster(id, owner, multiplier, now + duration, now, serverWide, activatedBy);
    }

    private Collection<ActiveBooster> replacedBy(ActiveBooster merged, boolean serverWide, UUID owner) {
        if (configs.config().strengthStacks()) return List.of();
        Collection<ActiveBooster> existing = serverWide ? server.values() : personal.getOrDefault(owner, Map.of()).values();
        return existing.stream().filter(value -> !value.id().equals(merged.id())).toList();
    }

    private void removeReplacedFromCache(ActiveBooster merged, Collection<ActiveBooster> replaced) {
        if (replaced.isEmpty()) return;
        if (merged.serverWide()) replaced.forEach(value -> server.remove(value.id(), value));
        else {
            Map<String, ActiveBooster> values = personal.get(merged.owner());
            if (values != null) replaced.forEach(value -> values.remove(value.id(), value));
        }
    }

    public CompletableFuture<Void> playerQuit(UUID playerId) {
        if (configs.config().offlineTimeContinues()) return CompletableFuture.completedFuture(null);
        pausedPlayers.add(playerId);
        return repository.pause(playerId, System.currentTimeMillis()).whenComplete((ignored, error) -> {
            if (error != null) pausedPlayers.remove(playerId);
        });
    }

    public CompletableFuture<Void> playerJoin(UUID playerId) {
        if (configs.config().offlineTimeContinues() && !pausedPlayers.contains(playerId)) {
            return CompletableFuture.completedFuture(null);
        }
        return repository.resume(playerId, System.currentTimeMillis()).thenAccept(extension -> {
            pausedPlayers.remove(playerId);
            if (extension > 0L) {
                Map<String, ActiveBooster> values = personal.get(playerId);
                if (values != null) values.replaceAll((id, booster) -> new ActiveBooster(booster.id(), booster.owner(),
                        booster.multiplier(), booster.expiresAt() + extension, booster.createdAt(), false, booster.activatedBy()));
                scheduled.replaceAll((id, booster) -> !playerId.equals(booster.owner()) ? booster
                        : new ActiveBooster(booster.id(), booster.owner(), booster.multiplier(),
                        booster.expiresAt() + extension, booster.createdAt(), false, booster.activatedBy(),
                        booster.startsAt() + extension, booster.enabled()));
            }
            if (plugin.multiplierService() != null) plugin.multiplierService().invalidate(playerId);
        });
    }

    public CompletableFuture<Void> remove(String id) {
        ActiveBooster booster = removeFromCaches(id);
        if (booster == null) return CompletableFuture.completedFuture(null);
        return repository.delete(booster).whenComplete((ignored, error) -> {
            if (error != null) putManaged(booster);
            else if (plugin.multiplierService() != null) plugin.multiplierService().invalidateAll();
        });
    }

    public CompletableFuture<Void> removeAll(UUID owner) {
        List<ActiveBooster> values = managedBoosters().stream().filter(booster -> owner.equals(booster.owner())).toList();
        values.forEach(booster -> removeFromCaches(booster.id()));
        return CompletableFuture.allOf(values.stream().map(repository::delete).toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> {
                    if (error != null) values.forEach(this::putManaged);
                    else if (plugin.multiplierService() != null) plugin.multiplierService().invalidate(owner);
                });
    }

    public CompletableFuture<BigDecimal> permanentMultiplier(UUID playerId) {
        BigDecimal cached = permanentMultipliers.get(playerId);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return repository.loadPermanentMultiplier(playerId).thenApply(value -> {
            permanentMultipliers.put(playerId, value); return value;
        });
    }

    public BigDecimal cachedPermanentMultiplier(UUID playerId) { return permanentMultipliers.getOrDefault(playerId, BigDecimal.ONE); }

    public CompletableFuture<Void> setPermanentMultiplier(UUID playerId, BigDecimal multiplier) {
        if (multiplier.signum() <= 0) return CompletableFuture.failedFuture(new IllegalArgumentException("Multiplier must be positive"));
        return repository.savePermanentMultiplier(playerId, multiplier).thenRun(() -> {
            permanentMultipliers.put(playerId, multiplier);
            if (plugin.multiplierService() != null) plugin.multiplierService().invalidate(playerId);
        });
    }

    private void expireNow() {
        if (plugin.gangs() != null) plugin.gangs().tickBoosters();
        long now = System.currentTimeMillis();
        List<ActiveBooster> starting = scheduled.values().stream()
                .filter(booster -> booster.active(now)).toList();
        for (ActiveBooster booster : starting) {
            if (!scheduled.remove(booster.id(), booster)) continue;
            RelicBoosterActivateEvent event = new RelicBoosterActivateEvent(booster.id(), booster.owner(),
                    booster.multiplier(), booster.expiresAt());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                ActiveBooster cancelled = new ActiveBooster(booster.id(), booster.owner(), booster.multiplier(),
                        booster.expiresAt(), booster.createdAt(), booster.serverWide(), booster.activatedBy(),
                        booster.startsAt(), false);
                disabled.put(cancelled.id(), cancelled);
                repository.save(cancelled).exceptionally(error -> {
                    plugin.getLogger().warning("Failed to disable cancelled scheduled booster " + booster.id());
                    return null;
                });
            } else putActive(booster);
        }
        List<ActiveBooster> expired = new ArrayList<>();
        server.values().removeIf(booster -> { if (booster.expired(now)) { expired.add(booster); return true; } return false; });
        for (var entry : personal.entrySet()) {
            if (pausedPlayers.contains(entry.getKey())) continue;
            entry.getValue().values().removeIf(booster -> { if (booster.expired(now)) { expired.add(booster); return true; } return false; });
        }
        if (!expired.isEmpty() && plugin.multiplierService() != null) plugin.multiplierService().invalidateAll();
        for (ActiveBooster booster : expired) {
            repository.delete(booster).exceptionally(error -> { plugin.getLogger().warning("Failed to delete expired booster " + booster.id()); return null; });
            Bukkit.getPluginManager().callEvent(new RelicBoosterExpireEvent(booster.id(), booster.owner()));
        }
        if (!starting.isEmpty() && plugin.multiplierService() != null) plugin.multiplierService().invalidateAll();
    }

    public BigDecimal gangMultiplier(UUID playerId,
            site.mcrelicworld.relicprison.gang.GangBooster.Type type) {
        return plugin.gangs() == null ? BigDecimal.ONE : plugin.gangs().multiplier(playerId, type);
    }

    public CompletableFuture<site.mcrelicworld.relicprison.gang.GangBooster> activateGang(UUID actorId,
            site.mcrelicworld.relicprison.gang.GangBooster.Type type, BigDecimal multiplier,
            long durationMillis, long delayMillis, String source) {
        if (plugin.gangs() == null) return CompletableFuture.failedFuture(
                new IllegalStateException("Gang system is unavailable"));
        return plugin.gangs().activateBooster(actorId, type, multiplier, durationMillis, delayMillis, source);
    }

    private void put(ActiveBooster booster) {
        putManaged(booster);
    }

    private void putManaged(ActiveBooster booster) {
        removeFromCaches(booster.id());
        long now = System.currentTimeMillis();
        if (!booster.enabled()) disabled.put(booster.id(), booster);
        else if (booster.scheduled(now)) scheduled.put(booster.id(), booster);
        else if (!booster.expired(now)) putActive(booster);
    }

    private void putActive(ActiveBooster booster) {
        if (booster.serverWide()) server.put(booster.id(), booster);
        else personal.computeIfAbsent(booster.owner(), ignored -> new ConcurrentHashMap<>()).put(booster.id(), booster);
    }

    private ActiveBooster removeFromCaches(String id) {
        ActiveBooster removed = scheduled.remove(id);
        if (removed == null) removed = disabled.remove(id);
        ActiveBooster serverBooster = server.remove(id);
        if (removed == null) removed = serverBooster;
        for (Map<String, ActiveBooster> values : personal.values()) {
            ActiveBooster personalBooster = values.remove(id);
            if (removed == null && personalBooster != null) removed = personalBooster;
        }
        return removed;
    }

    public Collection<ActiveBooster> managedBoosters() {
        List<ActiveBooster> result = new ArrayList<>();
        result.addAll(server.values());
        personal.values().forEach(values -> result.addAll(values.values()));
        result.addAll(scheduled.values());
        result.addAll(disabled.values());
        return result.stream().sorted(Comparator.comparing(ActiveBooster::startsAt)
                .thenComparing(ActiveBooster::id)).toList();
    }

    public java.util.Optional<ActiveBooster> managedBooster(String id) {
        return managedBoosters().stream().filter(booster -> booster.id().equalsIgnoreCase(id)).findFirst();
    }

    public CompletableFuture<ActivationResult> createManaged(String id, UUID owner, boolean serverWide,
                                                              BigDecimal multiplier, long durationMillis,
                                                              long startsAt, String activatedBy) {
        if (!Bukkit.isPrimaryThread()) return CompletableFuture.failedFuture(
                new IllegalStateException("Managed booster creation must start on the server thread"));
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) return CompletableFuture.completedFuture(
                ActivationResult.failure("Invalid booster ID"));
        if (managedBooster(id).isPresent()) return CompletableFuture.completedFuture(
                ActivationResult.failure("Duplicate booster ID"));
        BoosterConfig config = configs.config();
        if (multiplier == null || multiplier.signum() <= 0
                || multiplier.compareTo(config.maximumBoosterMultiplier()) > 0)
            return CompletableFuture.completedFuture(ActivationResult.failure("Multiplier exceeds the configured limit"));
        if (durationMillis <= 0 || durationMillis > config.maximumDuration().toMillis())
            return CompletableFuture.completedFuture(ActivationResult.failure("Duration exceeds the configured limit"));
        if (!serverWide && owner == null) return CompletableFuture.completedFuture(
                ActivationResult.failure("Personal booster needs an owner"));
        long now = System.currentTimeMillis();
        long start = Math.max(now, startsAt);
        ActiveBooster booster = new ActiveBooster(id.toLowerCase(java.util.Locale.ROOT), owner, multiplier,
                Math.addExact(start, durationMillis), now, serverWide, activatedBy, start, true);
        if (start <= now) {
            RelicBoosterActivateEvent event = new RelicBoosterActivateEvent(booster.id(), owner,
                    booster.multiplier(), booster.expiresAt());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return CompletableFuture.completedFuture(
                    ActivationResult.failure("Booster activation was cancelled"));
        }
        return repository.save(booster).thenApply(ignored -> {
            putManaged(booster);
            if (plugin.multiplierService() != null) plugin.multiplierService().invalidateAll();
            return ActivationResult.success(booster);
        });
    }

    public CompletableFuture<Void> updateManaged(ActiveBooster before, BigDecimal multiplier,
                                                  long startsAt, long expiresAt, boolean enabled) {
        return updateManaged(before, before.owner(), before.serverWide(), multiplier, startsAt, expiresAt, enabled);
    }

    public CompletableFuture<Void> updateManaged(ActiveBooster before, UUID owner, boolean serverWide,
                                                  BigDecimal multiplier, long startsAt, long expiresAt,
                                                  boolean enabled) {
        if (!Bukkit.isPrimaryThread()) return CompletableFuture.failedFuture(
                new IllegalStateException("Managed booster updates must start on the server thread"));
        BoosterConfig config = configs.config();
        long duration = expiresAt - startsAt;
        if (multiplier == null || multiplier.signum() <= 0
                || multiplier.compareTo(config.maximumBoosterMultiplier()) > 0)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Multiplier exceeds the configured limit"));
        if (duration <= 0 || duration > config.maximumDuration().toMillis())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Duration exceeds the configured limit"));
        ActiveBooster updated = new ActiveBooster(before.id(), owner, multiplier, expiresAt,
                before.createdAt(), serverWide, before.activatedBy(), startsAt, enabled);
        return repository.replace(before, updated).thenRun(() -> {
            putManaged(updated);
            if (plugin.multiplierService() != null) plugin.multiplierService().invalidateAll();
        });
    }

    public Collection<ActiveBooster> personalBoosters(UUID owner) {
        return List.copyOf(personal.getOrDefault(owner, Map.of()).values());
    }
    public Collection<ActiveBooster> serverBoosterRecords() { return List.copyOf(server.values()); }
    public int activePersonalBoosterCount() {
        return personal.values().stream().mapToInt(Map::size).sum();
    }
    public int activeServerBoosterCount() { return server.size(); }

    public BigDecimal strongestPersonalMultiplier(UUID owner) {
        return personalBoosters(owner).stream().map(ActiveBooster::multiplier).max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
    }

    public BigDecimal strongestServerMultiplier() {
        return server.values().stream().map(ActiveBooster::multiplier).max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
    }

    public long longestPersonalRemainingMillis(UUID owner) {
        long now = System.currentTimeMillis();
        return personalBoosters(owner).stream().mapToLong(booster -> Math.max(0L, booster.expiresAt() - now))
                .max().orElse(0L);
    }

    public long longestServerRemainingMillis() {
        long now = System.currentTimeMillis();
        return server.values().stream().mapToLong(booster -> Math.max(0L, booster.expiresAt() - now))
                .max().orElse(0L);
    }
    @Override public Collection<BoosterView> activeFor(UUID playerId) {
        List<BoosterView> result = new ArrayList<>();
        personalBoosters(playerId).forEach(value -> result.add(value.view()));
        server.values().forEach(value -> result.add(value.view()));
        return List.copyOf(result);
    }
    @Override public Collection<BoosterView> activeServerBoosters() { return server.values().stream().map(ActiveBooster::view).toList(); }

    public record ActivationResult(boolean success, String message, ActiveBooster booster) {
        static ActivationResult success(ActiveBooster booster) { return new ActivationResult(true, "", booster); }
        static ActivationResult failure(String message) { return new ActivationResult(false, message, null); }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }
}
