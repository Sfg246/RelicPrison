package site.mcrelicworld.relicprison.reset;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.MineResetService;
import site.mcrelicworld.relicprison.api.event.RelicMineCreatedEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineDeleteEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineDeletedEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineResetCompleteEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineResetFailEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineResetPrepareEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineResetStartEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineUpdateEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineUpdatedEvent;
import site.mcrelicworld.relicprison.api.model.MineResetState;
import site.mcrelicworld.relicprison.config.ResetEngineConfig;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.mine.MineResetConfig;
import site.mcrelicworld.relicprison.mine.MineServiceImpl;
import site.mcrelicworld.relicprison.mine.composition.CompositionEntry;
import site.mcrelicworld.relicprison.mine.composition.MineComposition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class MineResetServiceImpl implements MineResetService, Listener {
    private final RelicPrisonPlugin plugin;
    private final MineServiceImpl mines;
    private final MineRuntimeRepository repository;
    private final Map<String, MineRuntime> runtimes = new ConcurrentHashMap<>();
    private final Queue<ResetRequest> queue = new ArrayDeque<>();
    private final Map<String, PendingWarning> warnings = new HashMap<>();
    private final Map<String, ActiveReset> active = new LinkedHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();
    private final Set<String> recounting = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> automaticRetryAfter = new ConcurrentHashMap<>();
    private final Map<String, Integer> automaticRetryAttempts = new ConcurrentHashMap<>();
    private final ServerPerformanceMonitor performance = new ServerPerformanceMonitor();
    private int tickTask = -1;
    private int secondTask = -1;
    private int saveTask = -1;
    private boolean pausedForMspt;
    private final AtomicLong lastResetSliceNanos = new AtomicLong();
    private final AtomicLong lastItemsAdderSliceNanos = new AtomicLong();
    private final AtomicLong slowResetSlices = new AtomicLong();
    private final AtomicLong lastBlocksPerTick = new AtomicLong();
    private final AtomicLong lastVanillaBlocksPerTick = new AtomicLong();
    private final AtomicLong lastItemsAdderBlocksPerTick = new AtomicLong();

    public MineResetServiceImpl(RelicPrisonPlugin plugin, MineServiceImpl mines, MineRuntimeRepository repository) {
        this.plugin = plugin;
        this.mines = mines;
        this.repository = repository;
    }

    public void initialize() {
        initializeAsync().whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().severe("Mine reset runtime initialization failed: " + rootMessage(error));
                return;
            }
            startSchedulers();
        }));
    }

    public CompletableFuture<Void> initializeAsync() {
        return repository.load(mines.mines()).thenAccept(loaded -> {
            applyLoadedRuntimes(loaded);
            recoverInterruptedResets();
            reconcileMines();
        }).orTimeout(15, TimeUnit.SECONDS);
    }

    private void applyLoadedRuntimes(Map<String, MineRuntime> loaded) {
        runtimes.clear();
        runtimes.putAll(loaded);
    }

    public void startSchedulers() {
        if (tickTask != -1) Bukkit.getScheduler().cancelTask(tickTask);
        if (secondTask != -1) Bukkit.getScheduler().cancelTask(secondTask);
        if (saveTask != -1) Bukkit.getScheduler().cancelTask(saveTask);
        tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
        secondTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::second, 20L, 20L);
        long saveTicks = plugin.config().snapshot().resetEngine().runtimeSaveIntervalSeconds() * 20L;
        saveTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
                () -> repository.saveDirty(runtimes.values()).exceptionally(error -> {
                    plugin.getLogger().severe("Mine runtime save failed: " + rootMessage(error));
                    return null;
                }), saveTicks, saveTicks);
    }


    private void recoverInterruptedResets() {
        long retryAt = System.currentTimeMillis()
                + plugin.config().snapshot().resetEngine().failureRetryDelaySeconds() * 1000L;
        for (MineRuntime runtime : runtimes.values()) {
            MineResetState.State state = runtime.state();
            if (state == MineResetState.State.WARNING || state == MineResetState.State.QUEUED
                    || state == MineResetState.State.PREPARING || state == MineResetState.State.RESETTING
                    || state == MineResetState.State.COMPLETING) {
                runtime.resetFailed();
                automaticRetryAfter.put(runtime.mineId(), retryAt);
                plugin.getLogger().warning("Recovered interrupted mine reset for " + runtime.mineId()
                        + "; a full retry will be attempted after the configured delay.");
            }
        }
    }

    public void shutdown() {
        if (tickTask != -1) Bukkit.getScheduler().cancelTask(tickTask);
        if (secondTask != -1) Bukkit.getScheduler().cancelTask(secondTask);
        if (saveTask != -1) Bukkit.getScheduler().cancelTask(saveTask);
        await(repository.saveDirty(runtimes.values()), "mine runtime save");
        queue.clear();
        warnings.clear();
        active.clear();
        requested.clear();
        automaticRetryAfter.clear();
        automaticRetryAttempts.clear();
    }

    private void await(CompletableFuture<?> future, String operation) {
        try {
            future.orTimeout(plugin.config().snapshot().storage().shutdownFlushTimeoutSeconds(), TimeUnit.SECONDS).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during " + operation, ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new IllegalStateException("Timed out or failed during " + operation, ex);
        }
    }

    public void reconcileMines() {
        long now = System.currentTimeMillis();
        for (MineDefinition mine : mines.mines()) {
            MineRuntime runtime = runtimes.get(mine.id());
            if (runtime == null) {
                runtime = new MineRuntime(mine.id(), mine.volume(), 0, 0,
                        now + mine.resetConfig().intervalSeconds() * 1000L, 0, MineResetState.State.IDLE);
                runtime.markDirty();
                runtimes.put(mine.id(), runtime);
            } else {
                runtime.reconcileVolume(mine.volume());
                if (runtime.nextReset() <= 0) {
                    runtime.scheduleNext(now + mine.resetConfig().intervalSeconds() * 1000L);
                }
                if (runtime.state() == MineResetState.State.FAILED) {
                    automaticRetryAfter.putIfAbsent(mine.id(),
                            now + mine.resetConfig().retryDelaySeconds() * 1000L);
                }
            }
        }
        for (String id : List.copyOf(runtimes.keySet())) {
            if (mines.findMine(id).isPresent()) continue;
            removeRuntime(id);
        }
    }

    @Override public Optional<MineResetState> state(String mineId) {
        MineDefinition mine = mines.findMine(mineId).orElse(null);
        MineRuntime runtime = runtime(mineId);
        if (mine == null || runtime == null) return Optional.empty();
        ActiveReset reset = active.get(mine.id());
        long processed = reset == null ? 0 : reset.processed();
        return Optional.of(new MineResetState(mine.id(), runtime.state(), processed, mine.volume()));
    }

    @Override public boolean isResetting(String mineId) {
        MineRuntime runtime = runtime(mineId);
        if (runtime == null) return false;
        return runtime.state() == MineResetState.State.RESETTING
                || runtime.state() == MineResetState.State.PREPARING
                || runtime.state() == MineResetState.State.COMPLETING;
    }

    @Override public boolean requestReset(String mineId, String reason, boolean useWarnings) {
        MineDefinition mine = mines.findMine(mineId).orElse(null);
        if (mine == null || !mine.enabled() || !mine.resetConfig().enabled()) return false;
        if (plugin.mineStructures() != null && plugin.mineStructures().isLocked(mine)) return false;
        String id = mine.id();
        MineRuntime runtime = runtimes.get(id);
        if (runtime == null || !requested.add(id)) return false;
        if (!normalizeReason(reason).equals("timed") && !normalizeReason(reason).equals("percentage")) {
            automaticRetryAfter.remove(id);
        }
        List<Integer> configuredWarnings = useWarnings ? mine.resetConfig().warningSeconds() : List.of();
        int countdown = useWarnings ? mine.resetConfig().countdownSeconds() : 0;
        if (countdown > 0) {
            runtime.state(MineResetState.State.WARNING);
            PendingWarning pending = new PendingWarning(mine, normalizeReason(reason),
                    System.currentTimeMillis() + countdown * 1000L, configuredWarnings);
            warnings.put(id, pending);
        } else {
            runtime.state(MineResetState.State.QUEUED);
            queue.add(new ResetRequest(id, normalizeReason(reason)));
        }
        return true;
    }

    @Override public boolean cancelPendingReset(String mineId) {
        MineDefinition mine = mines.findMine(mineId).orElse(null);
        MineRuntime runtime = runtime(mineId);
        if (mine == null || runtime == null || active.containsKey(mine.id())) return false;
        boolean removed = warnings.remove(mine.id()) != null;
        removed |= queue.removeIf(request -> request.mineId().equals(mine.id()));
        if (!removed) return false;
        requested.remove(mine.id());
        automaticRetryAfter.remove(mine.id());
        runtime.state(MineResetState.State.IDLE);
        runtime.scheduleNext(System.currentTimeMillis() + mine.resetConfig().intervalSeconds() * 1000L);
        return true;
    }

    public long recordBroken(String mineId, long amount) {
        MineRuntime runtime = runtime(mineId);
        if (runtime == null || isResetting(mineId)) return -1;
        return runtime.recordBroken(amount);
    }

    @Override public long remainingBlocks(String mineId) {
        MineRuntime runtime = runtime(mineId);
        return runtime == null ? -1 : runtime.remainingBlocks();
    }

    @Override public long resetCount(String mineId) {
        MineRuntime runtime = runtime(mineId);
        return runtime == null ? -1 : runtime.resetCount();
    }

    @Override public long nextReset(String mineId) {
        MineRuntime runtime = runtime(mineId);
        return runtime == null ? -1 : runtime.nextReset();
    }

    public long lastResetDurationMillis(String mineId) {
        MineRuntime runtime = runtime(mineId);
        return runtime == null ? 0L : runtime.lastResetDurationMillis();
    }

    @Override public double minedPercentage(String mineId) {
        MineDefinition mine = mines.findMine(mineId).orElse(null);
        MineRuntime runtime = runtime(mineId);
        return mine == null || runtime == null ? -1 : runtime.minedPercentage(mine.volume());
    }

    public Collection<MineRuntime> runtimes() { return List.copyOf(runtimes.values()); }
    public int activeResetCount() { return active.size(); }
    public int queuedResetCount() { return queue.size() + warnings.size(); }
    public boolean pausedForMspt() { return pausedForMspt; }
    public long lastResetSliceMillis() { return lastResetSliceNanos.get() / 1_000_000L; }
    public long lastItemsAdderPlacementMillis() { return lastItemsAdderSliceNanos.get() / 1_000_000L; }
    public long slowResetSliceCount() { return slowResetSlices.get(); }
    public long lastBlocksProcessedPerTick() { return lastBlocksPerTick.get(); }
    public long lastVanillaBlocksProcessedPerTick() { return lastVanillaBlocksPerTick.get(); }
    public long lastItemsAdderBlocksProcessedPerTick() { return lastItemsAdderBlocksPerTick.get(); }

    private void second() {
        reconcileMines();
        long now = System.currentTimeMillis();
        processWarnings(now);
        for (MineDefinition mine : mines.mines()) {
            if (!mine.enabled() || !mine.resetConfig().enabled() || requested.contains(mine.id())) continue;
            MineRuntime runtime = runtimes.get(mine.id());
            if (runtime == null) continue;
            long retryAfter = automaticRetryAfter.getOrDefault(mine.id(), 0L);
            if (runtime.state() == MineResetState.State.FAILED) {
                if (retryAfter <= 0 || now < retryAfter) continue;
                runtime.state(MineResetState.State.IDLE);
                automaticRetryAfter.remove(mine.id());
            } else if (now < retryAfter) {
                continue;
            }
            MineResetConfig reset = mine.resetConfig();
            if (reset.timedEnabled() && runtime.nextReset() > 0 && now >= runtime.nextReset()) {
                requestReset(mine.id(), "timed", true);
                continue;
            }
            if (reset.percentageEnabled() && reset.minedPercentage() > 0
                    && runtime.minedPercentage(mine.volume()) >= reset.minedPercentage()) {
                requestReset(mine.id(), "percentage", true);
            } else if (plugin.config().snapshot().resetEngine().emptyMineTriggerEnabled()
                    && runtime.remainingBlocks() <= 0) {
                requestReset(mine.id(), "empty", true);
            }
        }
    }

    private void processWarnings(long now) {
        List<String> ready = new ArrayList<>();
        for (PendingWarning pending : warnings.values()) {
            long remaining = Math.max(0, (pending.executeAt() - now + 999) / 1000);
            for (int warning : pending.warningSeconds()) {
                if (remaining <= warning && pending.sent().add(warning) && warning > 0) {
                    notifyReset(pending.mine(), "reset-warning",
                            Map.of("mine", pending.mine().displayName(), "seconds", String.valueOf(warning)), true);
                }
            }
            if (now >= pending.executeAt()) ready.add(pending.mine().id());
        }
        for (String id : ready) {
            PendingWarning pending = warnings.remove(id);
            if (pending != null) {
                MineRuntime runtime = runtimes.get(id);
                if (runtime == null) {
                    requested.remove(id);
                    continue;
                }
                runtime.state(MineResetState.State.QUEUED);
                queue.add(new ResetRequest(id, pending.reason()));
            }
        }
    }

    private void tick() {
        ResetEngineConfig config = plugin.config().snapshot().resetEngine();
        double mspt = performance.currentMspt();
        if (mspt > 0) {
            if (!pausedForMspt && mspt >= config.pauseAboveMspt()) pausedForMspt = true;
            else if (pausedForMspt && mspt <= config.resumeBelowMspt()) pausedForMspt = false;
        }
        if (pausedForMspt) return;
        long tickBlocks = 0L;
        long tickItemsAdderBlocks = 0L;
        long tickVanillaBlocks = 0L;
        int attempts = queue.size();
        while (active.size() < config.maximumConcurrentMines() && attempts-- > 0 && !queue.isEmpty()) {
            ResetRequest request = queue.poll();
            if (request == null) continue;
            MineDefinition mine = mines.findMine(request.mineId()).orElse(null);
            if (mine != null && !worldLimitAllows(mine, config)) {
                queue.add(request);
                continue;
            }
            start(request, config);
        }
        if (active.isEmpty()) return;
        long perMineNanos = Math.max(100_000L,
                (long) (config.targetTimePerTickMillis() * 1_000_000L / active.size()));
        List<String> completed = new ArrayList<>();
        for (Map.Entry<String, ActiveReset> entry : List.copyOf(active.entrySet())) {
            ActiveReset reset = entry.getValue();
            try {
                ActiveReset.TickResult result = reset.tick(perMineNanos, config.minimumBlocksPerTick(),
                        config.maximumBlocksPerTick(),
                        (long) (config.itemsAdderTargetTimePerTickMillis() * 1_000_000L),
                        config.itemsAdderMinimumBlocksPerTick(), config.itemsAdderMaximumBlocksPerTick());
                lastResetSliceNanos.set(result.elapsedNanos());
                if (result.itemsAdderElapsedNanos() > 0L) lastItemsAdderSliceNanos.set(result.itemsAdderElapsedNanos());
                plugin.performanceMetrics().recordNanos("reset.slice", result.elapsedNanos());
                if (result.itemsAdderPlaced() > 0) {
                    plugin.performanceMetrics().recordNanos("reset.itemsadder-placement", result.itemsAdderElapsedNanos());
                }
                if (result.elapsedNanos() > perMineNanos) slowResetSlices.incrementAndGet();
                tickBlocks += result.placed();
                tickItemsAdderBlocks += result.itemsAdderPlaced();
                tickVanillaBlocks += Math.max(0, result.placed() - result.itemsAdderPlaced());
                if (result.complete()) completed.add(entry.getKey());
            } catch (RuntimeException ex) {
                fail(reset, ex);
                completed.add(entry.getKey());
            }
        }
        lastBlocksPerTick.set(tickBlocks);
        lastItemsAdderBlocksPerTick.set(tickItemsAdderBlocks);
        lastVanillaBlocksPerTick.set(tickVanillaBlocks);
        for (String id : completed) {
            ActiveReset reset = active.remove(id);
            if (reset != null && reset.runtime().state() != MineResetState.State.FAILED) complete(reset);
        }
    }

    private boolean worldLimitAllows(MineDefinition mine, ResetEngineConfig config) {
        int limit = config.perWorldConcurrentLimits().getOrDefault(mine.worldName().toLowerCase(Locale.ROOT),
                config.maximumConcurrentMines());
        int activeInWorld = 0;
        for (ActiveReset reset : active.values()) {
            if (reset.mine().worldId().equals(mine.worldId())) activeInWorld++;
        }
        return activeInWorld < limit;
    }

    private void start(ResetRequest request, ResetEngineConfig config) {
        MineDefinition mine = mines.findMine(request.mineId()).orElse(null);
        MineRuntime runtime = runtimes.get(request.mineId());
        if (mine == null || runtime == null || !mine.enabled()) {
            requested.remove(request.mineId());
            return;
        }
        if (plugin.mineStructures() != null && plugin.mineStructures().isLocked(mine)) {
            runtime.state(MineResetState.State.IDLE);
            requested.remove(mine.id());
            delayAutomaticRetry(mine.id());
            return;
        }
        RelicMineResetPrepareEvent prepare = new RelicMineResetPrepareEvent(mine.id(), request.reason());
        Bukkit.getPluginManager().callEvent(prepare);
        if (prepare.isCancelled()) {
            runtime.state(MineResetState.State.IDLE);
            requested.remove(mine.id());
            delayAutomaticRetry(mine.id());
            return;
        }
        World world = Bukkit.getWorld(mine.worldId());
        if (world == null) {
            failBeforeStart(mine, runtime, request.reason(), new IllegalStateException("Mine world is not loaded"));
            return;
        }
        runtime.state(MineResetState.State.PREPARING);
        runHooks(mine.resetConfig().beforeCommands(), mine, request.reason(), 0, null);
        if (mine.resetConfig().evacuatePlayers() && !evacuate(mine, world)) {
            failBeforeStart(mine, runtime, request.reason(),
                    new IllegalStateException("Unable to safely evacuate every player from the mine"));
            return;
        }
        List<CompositionEntry> eligibleEntries = mine.composition().entries().stream()
                .filter(entry -> entry.minimumPrestige() == null || mine.requiredPrestige() != null
                        && plugin.prestigeService().indexOf(mine.requiredPrestige())
                        >= plugin.prestigeService().indexOf(entry.minimumPrestige()))
                .toList();
        if (eligibleEntries.isEmpty()) {
            failBeforeStart(mine, runtime, request.reason(),
                    new IllegalStateException("No composition entries are eligible for the mine prestige tier"));
            return;
        }
        ActiveReset reset = new ActiveReset(mine, runtime, world, request.reason(), config.initialBlocksPerTick(),
                config.itemsAdderInitialBlocksPerTick(), plugin.itemsAdder(), new MineComposition(eligibleEntries));
        runtime.state(MineResetState.State.RESETTING);
        active.put(mine.id(), reset);
        Bukkit.getPluginManager().callEvent(new RelicMineResetStartEvent(mine.id(), request.reason(), mine.volume()));
        runHooks(mine.resetConfig().startCommands(), mine, request.reason(), 0, null);
        notifyReset(mine, "reset-started", Map.of("mine", mine.displayName()), false);
    }

    private void complete(ActiveReset reset) {
        MineDefinition mine = reset.mine();
        MineRuntime runtime = reset.runtime();
        runtime.state(MineResetState.State.COMPLETING);
        long now = System.currentTimeMillis();
        long duration = Math.max(0, now - reset.startedAt());
        long next = now + mine.resetConfig().intervalSeconds() * 1000L;
        runtime.resetCompleted(mine.volume(), now, duration, next);
        requested.remove(mine.id());
        automaticRetryAfter.remove(mine.id());
        automaticRetryAttempts.remove(mine.id());
        repository.save(runtime).exceptionally(error -> {
            plugin.getLogger().severe("Unable to persist reset state for " + mine.id() + ": " + rootMessage(error));
            return null;
        });
        Bukkit.getPluginManager().callEvent(new RelicMineResetCompleteEvent(mine.id(), reset.reason(), mine.volume(), duration));
        runHooks(mine.resetConfig().completeCommands(), mine, reset.reason(), duration, null);
        notifyReset(mine, "reset-complete", Map.of("mine", mine.displayName(), "duration", String.valueOf(duration)), false);
    }

    private void fail(ActiveReset reset, RuntimeException error) {
        reset.runtime().resetFailed();
        requested.remove(reset.mine().id());
        delayAutomaticRetry(reset.mine().id());
        persistFailure(reset, "resetting", error);
        runHooks(reset.mine().resetConfig().failedCommands(), reset.mine(), reset.reason(), 0, error.getMessage());
        Bukkit.getPluginManager().callEvent(new RelicMineResetFailEvent(reset.mine().id(), reset.reason(), rootMessage(error)));
        notifyReset(reset.mine(), "reset-failed", Map.of("mine", reset.mine().displayName(), "error", rootMessage(error)), false);
        if (reset.mine().resetConfig().recountBehavior() == MineResetConfig.RecountBehavior.AFTER_FAILURE) {
            Bukkit.getScheduler().runTask(plugin, () -> recount(reset.mine().id(), null, "failure"));
        }
        alertStaff(reset.mine(), rootMessage(error));
        plugin.getLogger().severe("Mine reset failed for " + reset.mine().id() + ": " + rootMessage(error));
    }

    private void failBeforeStart(MineDefinition mine, MineRuntime runtime, String reason, RuntimeException error) {
        runtime.resetFailed();
        requested.remove(mine.id());
        delayAutomaticRetry(mine.id());
        persistFailure(mine, runtime, reason, "preparing", 0, 0, 0, 0, System.currentTimeMillis(), error);
        runHooks(mine.resetConfig().failedCommands(), mine, reason, 0, error.getMessage());
        Bukkit.getPluginManager().callEvent(new RelicMineResetFailEvent(mine.id(), reason, rootMessage(error)));
        notifyReset(mine, "reset-failed", Map.of("mine", mine.displayName(), "error", rootMessage(error)), false);
        if (mine.resetConfig().recountBehavior() == MineResetConfig.RecountBehavior.AFTER_FAILURE) {
            Bukkit.getScheduler().runTask(plugin, () -> recount(mine.id(), null, "failure"));
        }
        alertStaff(mine, rootMessage(error));
    }

    private void persistFailure(ActiveReset reset, String stage, RuntimeException error) {
        persistFailure(reset.mine(), reset.runtime(), reset.reason(), stage, reset.processed(), reset.vanillaPlaced(),
                reset.itemsAdderPlaced(), reset.airPlaced(), reset.startedAt(), error);
    }

    private void persistFailure(MineDefinition mine, MineRuntime runtime, String reason, String stage, long processed,
                                long vanilla, long itemsAdder, long air, long startedAt, RuntimeException error) {
        long failedAt = System.currentTimeMillis();
        ResetFailureRecord failure = new ResetFailureRecord(UUID.randomUUID().toString(), mine.id(), reason, stage,
                startedAt, failedAt, Math.max(0, failedAt - startedAt), mine.volume(), processed, vanilla, itemsAdder,
                air, runtime.resetCount(), truncate(rootMessage(error)), true);
        repository.saveFailure(failure).exceptionally(saveError -> {
            plugin.getLogger().severe("Unable to persist reset failure for " + mine.id() + ": " + rootMessage(saveError));
            return null;
        });
    }

    private void alertStaff(MineDefinition mine, String error) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("relicprison.admin.mine.reset.alerts")) {
                plugin.messages().send(player, "reset-failed-staff", Map.of("mine", mine.displayName(), "error", error));
            }
        }
    }

    private boolean evacuate(MineDefinition mine, World world) {
        Location destination = mine.resetConfig().teleportDestination() == MineResetConfig.TeleportDestination.WORLD_SPAWN
                || mine.spawn() == null ? world.getSpawnLocation() : mine.spawn().toLocation(world);
        if (mine.resetConfig().requireOutsideDestination() && inside(mine, destination)) {
            plugin.getLogger().warning("Mine " + mine.id()
                    + " has a reset spawn inside its refill region; using world spawn instead.");
            destination = world.getSpawnLocation();
        }

        boolean success = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!inside(mine, player.getLocation())) continue;
            if (!player.teleport(destination) || inside(mine, player.getLocation())) {
                success = false;
                plugin.getLogger().warning("Unable to evacuate " + player.getName() + " from Mine " + mine.id());
                continue;
            }
            plugin.messages().send(player, "reset-evacuated", Map.of("mine", mine.displayName()));
        }

        // Never refill around a player. Abort the reset if another plugin cancelled or redirected a teleport.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (inside(mine, player.getLocation())) {
                success = false;
                plugin.getLogger().warning("Mine " + mine.id() + " reset was blocked because "
                        + player.getName() + " remained inside the refill region.");
            }
        }
        return success;
    }

    private static boolean inside(MineDefinition mine, Location location) {
        return location.getWorld() != null
                && location.getWorld().getUID().equals(mine.worldId())
                && mine.bounds().contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private void notifyReset(MineDefinition mine, String key, Map<String, String> placeholders, boolean warning) {
        ResetEngineConfig notification = plugin.config().snapshot().resetEngine();
        for (Player player : recipients(mine)) {
            if (notification.chatNotifications()) plugin.messages().send(player, key, placeholders);
            if (notification.titleNotifications()) {
                if (warning) {
                    player.sendTitle(plugin.messages().formatPlain("reset-warning-title", placeholders),
                            plugin.messages().formatPlain("reset-warning-subtitle", placeholders), 5, 30, 5);
                } else if (key.equals("reset-complete")) {
                    player.sendTitle(plugin.messages().formatPlain("reset-complete-title", placeholders),
                            plugin.messages().formatPlain("reset-complete-subtitle", placeholders), 5, 30, 10);
                } else if (key.equals("reset-failed")) {
                    player.sendTitle(plugin.messages().formatPlain("reset-failed-title", placeholders),
                            plugin.messages().formatPlain("reset-failed-subtitle", placeholders), 5, 30, 10);
                }
            }
            if (notification.actionBarNotifications()) {
                String actionKey = warning ? "reset-warning-actionbar"
                        : key.equals("reset-complete") ? "reset-complete-actionbar"
                        : key.equals("reset-failed") ? "reset-failed-actionbar"
                        : key.equals("reset-started") ? "reset-started-actionbar" : null;
                if (actionKey != null) sendActionBar(player, plugin.messages().formatPlain(actionKey, placeholders));
            }
            if (notification.soundNotifications()) {
                if (warning) player.playSound(player.getLocation(), notification.warningSound(), 0.7f, 1.2f);
                else if (key.equals("reset-complete")) {
                    player.playSound(player.getLocation(), notification.completionSound(), 0.8f, 1.1f);
                } else if (key.equals("reset-failed")) {
                    player.playSound(player.getLocation(), notification.failureSound(), 0.8f, 0.9f);
                }
            }
        }
    }

    private void sendActionBar(Player player, String message) {
        if (message == null || message.isBlank()) return;
        try {
            player.getClass().getMethod("sendActionBar", String.class).invoke(player, message);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().fine("Action-bar method is unavailable for " + player.getName());
        }
    }

    private Collection<Player> recipients(MineDefinition mine) {
        MineResetConfig config = mine.resetConfig();
        if (config.notificationScope() == MineResetConfig.NotificationScope.NONE) return List.of();
        List<Player> result = new ArrayList<>();
        World world = Bukkit.getWorld(mine.worldId());
        Location center = world == null ? null : new Location(world,
                (mine.bounds().minimum().x() + mine.bounds().maximum().x()) / 2.0,
                (mine.bounds().minimum().y() + mine.bounds().maximum().y()) / 2.0,
                (mine.bounds().minimum().z() + mine.bounds().maximum().z()) / 2.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (config.notificationScope() == MineResetConfig.NotificationScope.GLOBAL) {
                result.add(player);
                continue;
            }
            Location location = player.getLocation();
            if (location.getWorld() == null || !location.getWorld().getUID().equals(mine.worldId())) continue;
            if (config.notificationScope() == MineResetConfig.NotificationScope.MINE
                    && mine.bounds().contains(location.getBlockX(), location.getBlockY(), location.getBlockZ())) result.add(player);
            else if (config.notificationScope() == MineResetConfig.NotificationScope.RADIUS && center != null
                    && location.distanceSquared(center) <= (double) config.notificationRadius() * config.notificationRadius()) result.add(player);
        }
        return result;
    }

    private void runHooks(List<String> commands, MineDefinition mine, String reason, long duration, String error) {
        if (!plugin.config().snapshot().features().resetCommandHooks() || commands.isEmpty()) return;
        CommandSender console = Bukkit.getConsoleSender();
        for (String command : commands) {
            String translated = command
                    .replace("%mine%", mine.id())
                    .replace("%mine_display%", mine.displayName())
                    .replace("%reason%", reason)
                    .replace("%duration_ms%", String.valueOf(duration))
                    .replace("%error%", error == null ? "" : error);
            if (plugin.commandDispatch() == null) Bukkit.dispatchCommand(console, translated);
            else plugin.commandDispatch().dispatchConsole("reset-hooks", translated);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMineDelete(RelicMineDeleteEvent event) {
        String id = event.mine().id().toLowerCase(Locale.ROOT);
        if (requested.contains(id) || active.containsKey(id)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMineUpdate(RelicMineUpdateEvent event) {
        String id = event.previous().id().toLowerCase(Locale.ROOT);
        if (requested.contains(id) || active.containsKey(id)) event.setCancelled(true);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        UUID worldId = event.getWorld().getUID();
        List<String> affected = active.entrySet().stream()
                .filter(entry -> entry.getValue().mine().worldId().equals(worldId))
                .map(Map.Entry::getKey)
                .toList();
        for (String mineId : affected) {
            ActiveReset reset = active.remove(mineId);
            if (reset != null) {
                fail(reset, new IllegalStateException("World unloaded during reset"));
            }
        }
        queue.removeIf(request -> {
            MineDefinition mine = mines.findMine(request.mineId()).orElse(null);
            if (mine == null || !mine.worldId().equals(worldId)) return false;
            requested.remove(request.mineId());
            return true;
        });
        warnings.entrySet().removeIf(entry -> {
            MineDefinition mine = mines.findMine(entry.getKey()).orElse(null);
            if (mine == null || !mine.worldId().equals(worldId)) return false;
            requested.remove(entry.getKey());
            return true;
        });
    }

    @EventHandler
    public void onMineCreated(RelicMineCreatedEvent event) {
        if (!(event.mine() instanceof MineDefinition mine)) return;
        long now = System.currentTimeMillis();
        MineRuntime runtime = new MineRuntime(mine.id(), mine.volume(), 0, 0,
                now + mine.resetConfig().intervalSeconds() * 1000L, 0, MineResetState.State.IDLE);
        runtime.markDirty();
        runtimes.put(mine.id(), runtime);
    }

    @EventHandler
    public void onMineUpdated(RelicMineUpdatedEvent event) {
        if (!(event.current() instanceof MineDefinition mine)) return;
        MineRuntime runtime = runtimes.get(mine.id());
        if (runtime == null) {
            onMineCreated(new RelicMineCreatedEvent(mine));
            return;
        }
        runtime.reconcileVolume(mine.volume());
        if (runtime.nextReset() <= 0) {
            runtime.scheduleNext(System.currentTimeMillis() + mine.resetConfig().intervalSeconds() * 1000L);
        }
    }

    @EventHandler
    public void onMineDeleted(RelicMineDeletedEvent event) {
        removeRuntime(event.mine().id().toLowerCase(Locale.ROOT));
    }

    private void removeRuntime(String mineId) {
        warnings.remove(mineId);
        active.remove(mineId);
        requested.remove(mineId);
        recounting.remove(mineId);
        automaticRetryAfter.remove(mineId);
        queue.removeIf(request -> request.mineId().equals(mineId));
        runtimes.remove(mineId);
        repository.delete(mineId).exceptionally(error -> {
            plugin.getLogger().warning("Unable to delete stale mine runtime " + mineId + ": " + rootMessage(error));
            return null;
        });
    }

    public CompletableFuture<Boolean> retryFailedReset(String mineId, CommandSender actor) {
        MineDefinition mine = mines.findMine(mineId).orElse(null);
        if (mine == null) return CompletableFuture.completedFuture(false);
        return repository.latestRetryableFailure(mine.id()).thenCompose(optional -> {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (optional.isEmpty()) {
                        result.complete(false);
                        return;
                    }
                    ResetFailureRecord failure = optional.get();
                    MineRuntime runtime = runtime(mine.id());
                    if (runtime == null || runtime.state() != MineResetState.State.FAILED
                            || runtime.resetCount() != failure.resetCountAtFailure()
                            || requested.contains(mine.id()) || active.containsKey(mine.id())) {
                        result.complete(false);
                        return;
                    }
                    runtime.state(MineResetState.State.IDLE);
                    boolean queued = requestReset(mine.id(), "retry", false);
                    if (queued) {
                        repository.markFailureNotRetryable(failure.resetId()).exceptionally(error -> null);
                        if (actor != null) plugin.messages().send(actor, "reset-retry-queued",
                                Map.of("mine", mine.displayName()));
                    }
                    result.complete(queued);
                } catch (RuntimeException ex) {
                    result.completeExceptionally(ex);
                }
            });
            return result;
        });
    }

    public boolean recount(String mineId, CommandSender actor, String reason) {
        MineDefinition mine = mines.findMine(mineId).orElse(null);
        MineRuntime runtime = runtime(mineId);
        if (mine == null || runtime == null || isResetting(mineId) || !recounting.add(mine.id())) return false;
        World world = Bukkit.getWorld(mine.worldId());
        if (world == null) {
            recounting.remove(mine.id());
            return false;
        }
        ResetCursor cursor = new ResetCursor(mine.bounds(), MineResetConfig.ResetOrder.BOTTOM_TO_TOP);
        Set<String> allowed = mine.composition().entries().stream()
                .map(entry -> entry.block().qualifiedId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        int[] taskId = new int[]{-1};
        class RecountTask implements Runnable {
            long mineBlocks;
            long air;
            long vanilla;
            long custom;
            long foreign;
            long processed;

            @Override public void run() {
                int budget = plugin.config().snapshot().resetEngine().maximumBlocksPerTick();
                int slice = 0;
                while (cursor.hasNext() && slice++ < budget) {
                    count(world.getBlockAt(cursor.x(), cursor.y(), cursor.z()));
                    cursor.advance();
                    processed++;
                }
                if (processed % Math.max(1, budget * 20L) == 0 && actor != null) {
                    plugin.messages().info(actor, "Recounting Mine &f" + mine.id() + "&7: &b" + processed
                            + "&8/&f" + mine.volume() + " &7blocks scanned.");
                }
                if (!cursor.hasNext()) {
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    runtime.repairRemaining(mineBlocks, reason, actor == null ? "console" : actor.getName());
                    repository.save(runtime).exceptionally(error -> {
                        plugin.getLogger().severe("Unable to persist recount for " + mine.id() + ": " + rootMessage(error));
                        return null;
                    });
                    recounting.remove(mine.id());
                    if (actor != null) plugin.messages().send(actor, "mine-recount-complete",
                            Map.of("mine", mine.displayName(), "remaining", String.valueOf(mineBlocks),
                                    "air", String.valueOf(air), "vanilla", String.valueOf(vanilla),
                                    "itemsadder", String.valueOf(custom), "foreign", String.valueOf(foreign)));
                }
            }

            private void count(Block block) {
                Material type = block.getType();
                if (type.isAir()) {
                    air++;
                    if (allowed.contains("vanilla:air")) mineBlocks++;
                    return;
                }
                String vanillaKey = "vanilla:" + type.name().toLowerCase(Locale.ROOT);
                if (allowed.contains(vanillaKey)) {
                    vanilla++;
                    mineBlocks++;
                    return;
                }
                if (plugin.itemsAdder() != null && plugin.itemsAdder().connected()) {
                    Optional<String> customId = plugin.itemsAdder().customBlockId(block);
                    if (customId.isPresent() && allowed.contains("itemsadder:" + customId.get())) {
                        custom++;
                        mineBlocks++;
                        return;
                    }
                }
                foreign++;
            }
        }
        taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new RecountTask(), 1L, 1L);
        return true;
    }

    private void delayAutomaticRetry(String mineId) {
        MineDefinition mine = mines.findMine(mineId).orElse(null);
        if (mine == null) return;
        int attempt = automaticRetryAttempts.merge(mineId, 1, Integer::sum);
        if (attempt > mine.resetConfig().retryCount()) {
            automaticRetryAfter.remove(mineId);
            return;
        }
        long delay = mine.resetConfig().retryDelaySeconds() * 1000L;
        automaticRetryAfter.put(mineId, System.currentTimeMillis() + delay);
    }

    private MineRuntime runtime(String mineId) {
        if (mineId == null) return null;
        return runtimes.get(mineId.toLowerCase(Locale.ROOT));
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "manual" : reason.toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private record ResetRequest(String mineId, String reason) {}
    private record PendingWarning(MineDefinition mine, String reason, long executeAt,
                                  List<Integer> warningSeconds, Set<Integer> sent) {
        PendingWarning(MineDefinition mine, String reason, long executeAt, List<Integer> warningSeconds) {
            this(mine, reason, executeAt, List.copyOf(warningSeconds), ConcurrentHashMap.newKeySet());
        }
    }
}
