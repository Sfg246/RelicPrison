package site.mcrelicworld.relicprison.mine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.event.RelicMineDeleteEvent;
import site.mcrelicworld.relicprison.api.event.RelicMineUpdateEvent;
import site.mcrelicworld.relicprison.config.ResetEngineConfig;
import site.mcrelicworld.relicprison.integration.WorldEditStructureProvider;
import site.mcrelicworld.relicprison.logging.LogCategory;
import site.mcrelicworld.relicprison.mine.MineResetConfig.ResetOrder;
import site.mcrelicworld.relicprison.reset.ResetCursor;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MineStructureService implements Listener {
    private final RelicPrisonPlugin plugin;
    private final StructureOperationRepository repository;
    private final WorldEditStructureProvider worldEdit = new WorldEditStructureProvider();
    private final Map<UUID, StructureOperationRecord> operations = new ConcurrentHashMap<>();
    private final Set<String> lockedMines = ConcurrentHashMap.newKeySet();
    private final Set<Integer> activeTasks = ConcurrentHashMap.newKeySet();
    private final AtomicInteger internalMutationDepth = new AtomicInteger();

    public MineStructureService(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
        this.repository = new StructureOperationRepository(plugin);
    }

    public void initialize() throws Exception {
        operations.clear();
        operations.putAll(repository.load());
        boolean changed = false;
        for (StructureOperationRecord record : operations.values()) {
            if (record.stage().terminal()) continue;
            lockLoaded(record);
            if (record.failure() == null) {
                operations.put(record.id(), record.withFailureMessage(
                        "Operation was interrupted by a server stop and requires retry or rollback."));
                changed = true;
            }
            plugin.structuredLogger().warning(LogCategory.MINES,
                    "Recovered incomplete structure operation " + record.id() + " at stage " + record.stage() + '.');
        }
        if (changed) saveOperations();
    }

    public void shutdown() {
        for (Integer taskId : List.copyOf(activeTasks)) Bukkit.getScheduler().cancelTask(taskId);
        activeTasks.clear();
        for (StructureOperationRecord record : List.copyOf(operations.values())) {
            if (record.stage().terminal()) continue;
            operations.put(record.id(), record.withFailureMessage(
                    "Operation was interrupted by plugin shutdown and requires retry or rollback."));
        }
        try {
            saveOperations();
        } catch (Exception ex) {
            plugin.getLogger().severe("Unable to persist structure operations during shutdown: " + rootMessage(ex));
        }
    }

    public Collection<StructureOperationRecord> operations() {
        return operations.values().stream()
                .sorted(Comparator.comparingLong(StructureOperationRecord::createdAt).reversed())
                .toList();
    }

    public Optional<StructureOperationRecord> operation(UUID operationId) {
        return Optional.ofNullable(operations.get(operationId));
    }

    public boolean isLocked(String mineId) {
        if (mineId == null) return false;
        return lockedMines.contains(MineDefinition.normalizeId(mineId));
    }

    public boolean isLocked(MineDefinition mine) {
        if (mine == null) return false;
        return isLocked(mine.id()) || intersectsLockedRegion(mine.worldId(), mine.bounds(), null);
    }

    public boolean intersectsLockedRegion(UUID worldId, Cuboid bounds) {
        return intersectsLockedRegion(worldId, bounds, null);
    }

    public CompletableFuture<StructureOperationRecord> execute(UUID staffId, MineDefinition source,
                                                                MineDefinition target, boolean createTarget) {
        requireMainThread();
        validateOperation(source, target, createTarget);
        StructureOperationType type = createTarget ? StructureOperationType.COPY : StructureOperationType.MOVE;
        StructureOperationRecord record = new StructureOperationRecord(
                UUID.randomUUID(), type, StructureOperationStage.PREPARED, staffId, source, target,
                System.currentTimeMillis(), System.currentTimeMillis(), 0, 0, "PENDING", null);
        acquire(record);
        try {
            evacuatePlayers(record);
            StructureOperationRecord prepared = persist(record);
            return requireEmptyDestination(prepared)
                    .thenCompose(ignored -> runSync(() -> {
                        registerDisabledTarget(prepared);
                        return persist(prepared.withStage(StructureOperationStage.TARGET_REGISTERED));
                    }))
                    .thenCompose(this::copyAndActivate)
                    .exceptionallyCompose(error -> handleFailure(
                            operations.getOrDefault(record.id(), record), error));
        } catch (Throwable error) {
            return handleFailure(record, error);
        }
    }

    public CompletableFuture<StructureOperationRecord> retry(UUID operationId) {
        requireMainThread();
        StructureOperationRecord record = requireRecoverable(operationId).withFailure(null);
        if (record.stage() == StructureOperationStage.ROLLING_BACK) {
            throw new IllegalArgumentException("Operation is in rollback recovery; use the rollback action again");
        }
        if (!lockedMines.contains(record.source().id())) acquire(record);
        try {
            if (record.type() == StructureOperationType.COPY && targetIsActive(record)) {
                return CompletableFuture.completedFuture(complete(record));
            }
            if (record.type() == StructureOperationType.MOVE && targetIsActive(record)) {
                StructureOperationRecord clearing = persist(record.withStage(StructureOperationStage.CLEARING_SOURCE));
                return clearRegion(clearing.source().worldId(), clearing.source().bounds(), clearing.provider())
                        .thenApply(cleared -> complete(clearing.withProgress(
                                clearing.copiedBlocks(), cleared, clearing.provider())));
            }
            if (record.stage().ordinal() < StructureOperationStage.COPYING.ordinal()) {
                return requireEmptyDestination(record)
                        .thenCompose(ignored -> runSync(() -> {
                            ensureDisabledTarget(record);
                            return persist(record.withStage(StructureOperationStage.TARGET_REGISTERED));
                        }))
                        .thenCompose(this::copyAndActivate)
                        .exceptionallyCompose(error -> handleFailure(
                                operations.getOrDefault(record.id(), record), error));
            }
            ensureDisabledTarget(record);
            StructureOperationRecord registered = persist(record.withStage(StructureOperationStage.TARGET_REGISTERED));
            return clearRegion(registered.target().worldId(), registered.target().bounds(), registered.provider())
                    .thenCompose(ignored -> copyAndActivate(registered))
                    .exceptionallyCompose(error -> handleFailure(
                            operations.getOrDefault(record.id(), record), error));
        } catch (Throwable error) {
            return handleFailure(record, error);
        }
    }

    public CompletableFuture<StructureOperationRecord> rollback(UUID operationId) {
        requireMainThread();
        StructureOperationRecord record = requireRecoverable(operationId);
        if (!lockedMines.contains(record.source().id())) acquire(record);
        return rollbackInternal(record, true);
    }

    private CompletableFuture<StructureOperationRecord> copyAndActivate(StructureOperationRecord record) {
        StructureOperationRecord copying;
        try {
            copying = persist(record.withStage(StructureOperationStage.COPYING));
        } catch (Exception ex) {
            return handleFailure(record, ex);
        }
        return copyRegion(copying.source().worldId(), copying.source().bounds(),
                        copying.target().worldId(), copying.target().bounds())
                .thenCompose(work -> runSync(() -> {
                    StructureOperationRecord copied = persist(copying.withProgress(
                            work.blocks(), copying.clearedBlocks(), work.provider())
                            .withStage(StructureOperationStage.DESTINATION_COPIED));
                    internalMutation(() -> plugin.mineService().update(copied.target()));
                    return persist(copied.withStage(StructureOperationStage.TARGET_ACTIVATED));
                }))
                .thenCompose(activated -> {
                    if (activated.type() == StructureOperationType.COPY) {
                        return CompletableFuture.completedFuture(complete(activated));
                    }
                    StructureOperationRecord clearing;
                    try {
                        clearing = persist(activated.withStage(StructureOperationStage.CLEARING_SOURCE));
                    } catch (Exception ex) {
                        return CompletableFuture.failedFuture(ex);
                    }
                    return clearRegion(clearing.source().worldId(), clearing.source().bounds(), clearing.provider())
                            .thenApply(cleared -> complete(clearing.withProgress(
                                    clearing.copiedBlocks(), cleared, clearing.provider())));
                })
                .exceptionallyCompose(error -> handleFailure(
                        operations.getOrDefault(record.id(), record), error));
    }

    private CompletableFuture<StructureOperationRecord> rollbackInternal(StructureOperationRecord original,
                                                                          boolean requestedByStaff) {
        StructureOperationRecord rolling;
        try {
            rolling = persist(original.withStage(StructureOperationStage.ROLLING_BACK));
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
        CompletableFuture<Void> restore;
        if (rolling.type() == StructureOperationType.MOVE && targetIsActive(rolling)) {
            restore = clearRegion(rolling.source().worldId(), rolling.source().bounds(), rolling.provider())
                    .thenCompose(ignored -> copyRegion(rolling.target().worldId(), rolling.target().bounds(),
                            rolling.source().worldId(), rolling.source().bounds()))
                    .thenCompose(ignored -> runSync(() -> {
                        internalMutation(() -> plugin.mineService().update(rolling.source()));
                        return null;
                    }));
        } else {
            restore = runSync(() -> {
                if (rolling.type() == StructureOperationType.MOVE) {
                    internalMutation(() -> plugin.mineService().update(rolling.source()));
                }
                return null;
            });
        }
        boolean targetMayContainCopiedBlocks = original.stage().ordinal() >= StructureOperationStage.COPYING.ordinal()
                || targetIsActive(original);
        CompletableFuture<Long> clearTarget = restore.thenCompose(ignored -> targetMayContainCopiedBlocks
                ? clearRegion(rolling.target().worldId(), rolling.target().bounds(), rolling.provider())
                : CompletableFuture.completedFuture(0L));
        return clearTarget.thenCompose(ignored -> runSync(() -> {
                    if (rolling.type() == StructureOperationType.COPY
                            && plugin.mineService().findMine(rolling.target().id()).isPresent()) {
                        internalMutation(() -> plugin.mineService().delete(rolling.target().id()));
                    }
                    StructureOperationRecord rolledBack = persist(
                            rolling.withFailure(null).withStage(StructureOperationStage.ROLLED_BACK));
                    release(rolledBack);
                    plugin.structuredLogger().info(LogCategory.MINES,
                            "Structure operation " + rolledBack.id() + " rolled back"
                                    + (requestedByStaff ? " by staff." : " automatically."));
                    return rolledBack;
                }))
                .exceptionallyCompose(error -> {
                    StructureOperationRecord failed = current(original.id()).withFailure(root(error));
                    try {
                        persist(failed);
                    } catch (Exception saveError) {
                        error.addSuppressed(saveError);
                    }
                    return CompletableFuture.failedFuture(error);
                });
    }

    private CompletableFuture<StructureOperationRecord> handleFailure(StructureOperationRecord record, Throwable error) {
        Throwable cause = root(error);
        boolean journalExists = operations.containsKey(record.id());
        StructureOperationRecord current = operations.getOrDefault(record.id(), record).withFailure(cause);
        try {
            persist(current);
            journalExists = true;
        } catch (Exception saveError) {
            cause.addSuppressed(saveError);
        }
        plugin.structuredLogger().severe(LogCategory.MINES,
                "Structure operation " + current.id() + " failed at " + current.stage() + ": " + rootMessage(cause));
        if (!journalExists && current.stage() == StructureOperationStage.PREPARED) {
            release(current);
            return CompletableFuture.failedFuture(cause);
        }
        if (current.stage().ordinal() < StructureOperationStage.TARGET_ACTIVATED.ordinal()) {
            return rollbackInternal(current, false).exceptionallyCompose(rollbackError -> {
                cause.addSuppressed(root(rollbackError));
                return CompletableFuture.failedFuture(cause);
            });
        }
        return CompletableFuture.failedFuture(cause);
    }

    private void registerDisabledTarget(StructureOperationRecord record) throws Exception {
        MineDefinition disabled = record.target().withEnabled(false);
        if (record.type() == StructureOperationType.COPY) {
            internalMutation(() -> plugin.mineService().create(disabled));
        } else {
            internalMutation(() -> plugin.mineService().update(disabled));
        }
    }

    private void ensureDisabledTarget(StructureOperationRecord record) throws Exception {
        MineDefinition disabled = record.target().withEnabled(false);
        Optional<MineDefinition> current = plugin.mineService().findMine(record.target().id());
        if (record.type() == StructureOperationType.COPY && current.isEmpty()) {
            internalMutation(() -> plugin.mineService().create(disabled));
        } else {
            internalMutation(() -> plugin.mineService().update(disabled));
        }
    }

    private boolean targetIsActive(StructureOperationRecord record) {
        MineDefinition current = plugin.mineService().findMine(record.target().id()).orElse(null);
        return current != null && current.enabled() && current.worldId().equals(record.target().worldId())
                && current.bounds().equals(record.target().bounds());
    }

    private CompletableFuture<Void> requireEmptyDestination(StructureOperationRecord record) {
        World destination = Bukkit.getWorld(record.target().worldId());
        if (destination == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Destination world is not loaded: " + record.target().worldName()));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        ResetCursor cursor = new ResetCursor(record.target().bounds(), ResetOrder.BOTTOM_TO_TOP);
        int[] taskId = {-1};
        Runnable scanner = () -> {
            try {
                int budget = Math.max(1, plugin.config().snapshot().resetEngine().maximumBlocksPerTick());
                int processed = 0;
                while (cursor.hasNext() && processed++ < budget) {
                    Block block = destination.getBlockAt(cursor.x(), cursor.y(), cursor.z());
                    if (!block.getType().isAir()) {
                        throw new IllegalStateException("Structure destination must be empty; found "
                                + block.getType() + " at " + block.getX() + ',' + block.getY() + ',' + block.getZ());
                    }
                    cursor.advance();
                }
                if (!cursor.hasNext()) finishTask(taskId[0], future, null);
            } catch (Throwable error) {
                finishTask(taskId[0], future, error);
            }
        };
        taskId[0] = schedule(scanner);
        return future;
    }

    private CompletableFuture<BlockWork> copyRegion(UUID sourceWorldId, Cuboid sourceBounds,
                                                     UUID destinationWorldId, Cuboid destinationBounds) {
        World sourceWorld = Bukkit.getWorld(sourceWorldId);
        World destinationWorld = Bukkit.getWorld(destinationWorldId);
        if (sourceWorld == null || destinationWorld == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Source or destination world is not loaded"));
        }
        boolean canUseWorldEdit = plugin.config().snapshot().features().worldEditFawe()
                && worldEdit.available()
                && (plugin.itemsAdder() == null || !plugin.itemsAdder().enabled());
        if (canUseWorldEdit) {
            try {
                worldEdit.copy(sourceWorld, sourceBounds, destinationWorld, destinationBounds);
                return CompletableFuture.completedFuture(new BlockWork(sourceBounds.volume(), worldEdit.providerName()));
            } catch (ReflectiveOperationException | RuntimeException error) {
                plugin.getLogger().warning(worldEdit.providerName() + " structure copy failed; using sliced Bukkit fallback: "
                        + rootMessage(error));
                return clearBukkit(destinationWorld, destinationBounds)
                        .thenCompose(ignored -> copyBukkit(sourceWorld, sourceBounds, destinationWorld, destinationBounds));
            }
        }
        return copyBukkit(sourceWorld, sourceBounds, destinationWorld, destinationBounds);
    }

    private CompletableFuture<Long> clearRegion(UUID worldId, Cuboid bounds, String preferredProvider) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) return CompletableFuture.failedFuture(new IllegalStateException("World is not loaded: " + worldId));
        boolean useWorldEdit = ("FAWE".equals(preferredProvider) || "WORLDEDIT".equals(preferredProvider))
                && worldEdit.available() && (plugin.itemsAdder() == null || !plugin.itemsAdder().enabled());
        if (useWorldEdit) {
            try {
                worldEdit.clear(world, bounds);
                return CompletableFuture.completedFuture(bounds.volume());
            } catch (ReflectiveOperationException | RuntimeException error) {
                plugin.getLogger().warning("WorldEdit clear failed; using sliced Bukkit fallback: " + rootMessage(error));
            }
        }
        return clearBukkit(world, bounds);
    }

    private CompletableFuture<BlockWork> copyBukkit(World sourceWorld, Cuboid sourceBounds,
                                                     World destinationWorld, Cuboid destinationBounds) {
        CompletableFuture<BlockWork> future = new CompletableFuture<>();
        ResetCursor cursor = new ResetCursor(sourceBounds, ResetOrder.BOTTOM_TO_TOP);
        Location sourceLocation = new Location(sourceWorld, 0, 0, 0);
        Location destinationLocation = new Location(destinationWorld, 0, 0, 0);
        long[] copied = {0};
        int[] taskId = {-1};
        Runnable copier = () -> {
            try {
                ResetEngineConfig config = plugin.config().snapshot().resetEngine();
                int budget = Math.max(1, config.maximumBlocksPerTick());
                int slice = 0;
                while (cursor.hasNext() && slice++ < budget) {
                    int dx = cursor.x() - sourceBounds.minimum().x();
                    int dy = cursor.y() - sourceBounds.minimum().y();
                    int dz = cursor.z() - sourceBounds.minimum().z();
                    copyBlock(sourceWorld, destinationWorld, cursor.x(), cursor.y(), cursor.z(),
                            destinationBounds.minimum().x() + dx,
                            destinationBounds.minimum().y() + dy,
                            destinationBounds.minimum().z() + dz,
                            sourceLocation, destinationLocation);
                    copied[0]++;
                    cursor.advance();
                }
                if (!cursor.hasNext()) finishTask(taskId[0], future, new BlockWork(copied[0], "BUKKIT"), null);
            } catch (Throwable error) {
                finishTask(taskId[0], future, null, error);
            }
        };
        taskId[0] = schedule(copier);
        return future;
    }

    private CompletableFuture<Long> clearBukkit(World world, Cuboid bounds) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        ResetCursor cursor = new ResetCursor(bounds, ResetOrder.BOTTOM_TO_TOP);
        Location location = new Location(world, 0, 0, 0);
        long[] cleared = {0};
        int[] taskId = {-1};
        Runnable clearer = () -> {
            try {
                int budget = Math.max(1, plugin.config().snapshot().resetEngine().maximumBlocksPerTick());
                int slice = 0;
                while (cursor.hasNext() && slice++ < budget) {
                    clearBlock(world, cursor.x(), cursor.y(), cursor.z(), location);
                    cleared[0]++;
                    cursor.advance();
                }
                if (!cursor.hasNext()) finishTask(taskId[0], future, cleared[0], null);
            } catch (Throwable error) {
                finishTask(taskId[0], future, null, error);
            }
        };
        taskId[0] = schedule(clearer);
        return future;
    }

    private void copyBlock(World sourceWorld, World destinationWorld, int sourceX, int sourceY, int sourceZ,
                           int destinationX, int destinationY, int destinationZ,
                           Location sourceLocation, Location destinationLocation) {
        Block sourceBlock = sourceWorld.getBlockAt(sourceX, sourceY, sourceZ);
        Block destinationBlock = destinationWorld.getBlockAt(destinationX, destinationY, destinationZ);
        set(sourceLocation, sourceX, sourceY, sourceZ);
        set(destinationLocation, destinationX, destinationY, destinationZ);
        if (plugin.itemsAdder() != null && plugin.itemsAdder().connected()) {
            Optional<String> customId = plugin.itemsAdder().customBlockId(sourceBlock);
            if (customId.isPresent()) {
                destinationBlock.setType(Material.AIR, false);
                if (!plugin.itemsAdder().placeBlock(customId.get(), destinationLocation)) {
                    throw new IllegalStateException("Unable to place ItemsAdder block " + customId.get());
                }
                return;
            }
            plugin.itemsAdder().removeBlock(destinationLocation);
        }
        BlockData blockData = sourceBlock.getBlockData().clone();
        destinationBlock.setBlockData(blockData, false);
    }

    private void clearBlock(World world, int x, int y, int z, Location location) {
        set(location, x, y, z);
        if (plugin.itemsAdder() != null && plugin.itemsAdder().connected()) plugin.itemsAdder().removeBlock(location);
        world.getBlockAt(x, y, z).setType(Material.AIR, false);
    }

    private int schedule(Runnable runnable) {
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, runnable, 1L, 1L);
        activeTasks.add(taskId);
        return taskId;
    }

    private <T> void finishTask(int taskId, CompletableFuture<T> future, T value, Throwable error) {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            activeTasks.remove(taskId);
        }
        if (error == null) future.complete(value);
        else future.completeExceptionally(error);
    }

    private void finishTask(int taskId, CompletableFuture<Void> future, Throwable error) {
        finishTask(taskId, future, null, error);
    }

    private <T> CompletableFuture<T> runSync(CheckedSupplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Throwable error) {
                return CompletableFuture.failedFuture(error);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private StructureOperationRecord complete(StructureOperationRecord record) {
        try {
            StructureOperationRecord completed = persist(record.withFailure(null)
                    .withStage(StructureOperationStage.COMPLETED));
            release(completed);
            plugin.structuredLogger().info(LogCategory.MINES,
                    "Structure operation " + completed.id() + " completed using " + completed.provider()
                            + "; copied=" + completed.copiedBlocks() + ", cleared=" + completed.clearedBlocks() + '.');
            return completed;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist completed structure operation", ex);
        }
    }

    private StructureOperationRecord persist(StructureOperationRecord record) throws Exception {
        StructureOperationRecord previous = operations.put(record.id(), record);
        try {
            saveOperations();
            return record;
        } catch (Exception ex) {
            if (previous == null) operations.remove(record.id(), record);
            else operations.put(record.id(), previous);
            throw ex;
        }
    }

    private void saveOperations() throws Exception {
        repository.save(operations.values());
    }

    private StructureOperationRecord current(UUID operationId) {
        StructureOperationRecord record = operations.get(operationId);
        if (record == null) throw new IllegalStateException("Unknown structure operation " + operationId);
        return record;
    }

    private StructureOperationRecord requireRecoverable(UUID operationId) {
        StructureOperationRecord record = current(operationId);
        if (!record.recoverable()) {
            throw new IllegalArgumentException("Structure operation " + operationId + " is already " + record.stage());
        }
        return record;
    }

    private void validateOperation(MineDefinition source, MineDefinition target, boolean createTarget) {
        MineDefinition registeredSource = plugin.mineService().findMine(source.id())
                .orElseThrow(() -> new IllegalArgumentException("Source mine does not exist: " + source.id()));
        if (!registeredSource.worldId().equals(source.worldId()) || !registeredSource.bounds().equals(source.bounds())) {
            throw new IllegalArgumentException("Source mine changed after the structure operation was prepared");
        }
        if (!sameDimensions(source.bounds(), target.bounds())) {
            throw new IllegalArgumentException("Destination selection must match source mine dimensions");
        }
        if (source.worldId().equals(target.worldId()) && source.bounds().intersects(target.bounds())) {
            throw new IllegalArgumentException("Structure source and destination cannot overlap");
        }
        if (plugin.mineResets().isResetting(source.id())) {
            throw new IllegalArgumentException("Mine structure operations cannot run during an active reset");
        }
        if (isLocked(source.id()) || isLocked(target.id())) {
            throw new IllegalArgumentException("A structure operation is already using this mine");
        }
        if (Bukkit.getWorld(source.worldId()) == null) {
            throw new IllegalArgumentException("Source world is not loaded: " + source.worldName());
        }
        if (Bukkit.getWorld(target.worldId()) == null) {
            throw new IllegalArgumentException("Destination world is not loaded: " + target.worldName());
        }
        if (createTarget && plugin.mineService().findMine(target.id()).isPresent()) {
            throw new IllegalArgumentException("Mine already exists: " + target.id());
        }
        for (MineDefinition existing : plugin.mineService().mines()) {
            if (existing.id().equals(source.id())) continue;
            if (existing.worldId().equals(target.worldId()) && existing.bounds().intersects(target.bounds())) {
                throw new IllegalArgumentException("Structure destination overlaps existing mine " + existing.id());
            }
        }
        if (intersectsLockedRegion(target.worldId(), target.bounds(), null)) {
            throw new IllegalArgumentException("Another structure operation already locks the destination region");
        }
    }

    private synchronized void acquire(StructureOperationRecord record) {
        if (lockedMines.contains(record.source().id()) || lockedMines.contains(record.target().id())
                || intersectsLockedRegion(record.source().worldId(), record.source().bounds(), record.id())
                || intersectsLockedRegion(record.target().worldId(), record.target().bounds(), record.id())) {
            throw new IllegalStateException("Mine structure lock is already held");
        }
        lockedMines.add(record.source().id());
        lockedMines.add(record.target().id());
    }

    private boolean intersectsLockedRegion(UUID worldId, Cuboid bounds, UUID ignoredOperation) {
        for (StructureOperationRecord existing : operations.values()) {
            if (!existing.recoverable() || existing.id().equals(ignoredOperation)) continue;
            if (existing.source().worldId().equals(worldId) && existing.source().bounds().intersects(bounds)) return true;
            if (existing.target().worldId().equals(worldId) && existing.target().bounds().intersects(bounds)) return true;
        }
        return false;
    }

    private void lockLoaded(StructureOperationRecord record) {
        lockedMines.add(record.source().id());
        lockedMines.add(record.target().id());
    }

    private void release(StructureOperationRecord record) {
        lockedMines.remove(record.source().id());
        lockedMines.remove(record.target().id());
    }

    private void internalMutation(CheckedRunnable action) throws Exception {
        internalMutationDepth.incrementAndGet();
        try {
            action.run();
        } finally {
            internalMutationDepth.decrementAndGet();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMineUpdate(RelicMineUpdateEvent event) {
        if (internalMutationDepth.get() > 0) return;
        if (isLocked(event.previous().id())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMineDelete(RelicMineDeleteEvent event) {
        if (internalMutationDepth.get() > 0) return;
        if (isLocked(event.mine().id())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLocked(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isLocked(event.getBlockPlaced())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockFlow(BlockFromToEvent event) {
        if (isLocked(event.getBlock()) || isLocked(event.getToBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> isLocked(block)
                || isLocked(block.getRelative(event.getDirection())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> isLocked(block)
                || isLocked(block.getRelative(event.getDirection())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isLocked);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isLocked);
    }

    private boolean isLocked(Block block) {
        UUID worldId = block.getWorld().getUID();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        for (StructureOperationRecord record : operations.values()) {
            if (!record.recoverable()) continue;
            if (record.source().worldId().equals(worldId) && record.source().bounds().contains(x, y, z)) return true;
            if (record.target().worldId().equals(worldId) && record.target().bounds().contains(x, y, z)) return true;
        }
        return false;
    }

    private void evacuatePlayers(StructureOperationRecord record) {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            UUID worldId = player.getWorld().getUID();
            boolean inSource = record.source().worldId().equals(worldId)
                    && record.source().bounds().contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            boolean inTarget = record.target().worldId().equals(worldId)
                    && record.target().bounds().contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            if (!inSource && !inTarget) continue;
            Location destination = safeEvacuation(player.getWorld(), record);
            if (!player.teleport(destination)) {
                throw new IllegalStateException("Unable to evacuate " + player.getName()
                        + " before structure operation " + record.id());
            }
        }
    }

    private static Location safeEvacuation(World world, StructureOperationRecord record) {
        Location spawn = world.getSpawnLocation().clone();
        spawn.add(0.5, 0.0, 0.5);
        if (!contains(record, world.getUID(), spawn)) return spawn;
        Cuboid occupied = record.source().worldId().equals(world.getUID())
                ? record.source().bounds() : record.target().bounds();
        int x = occupied.maximum().x() + 3;
        int z = occupied.maximum().z() + 3;
        int y = Math.max(world.getMinHeight() + 1,
                Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(x, z) + 1));
        Location fallback = new Location(world, x + 0.5, y, z + 0.5);
        if (contains(record, world.getUID(), fallback)) {
            throw new IllegalStateException("No safe evacuation point is available outside the structure regions");
        }
        return fallback;
    }

    private static boolean contains(StructureOperationRecord record, UUID worldId, Location location) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return (record.source().worldId().equals(worldId) && record.source().bounds().contains(x, y, z))
                || (record.target().worldId().equals(worldId) && record.target().bounds().contains(x, y, z));
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Structure operations must start on the server thread");
    }

    private static void set(Location location, int x, int y, int z) {
        location.setX(x);
        location.setY(y);
        location.setZ(z);
    }

    private static boolean sameDimensions(Cuboid first, Cuboid second) {
        return dimension(first.minimum().x(), first.maximum().x()) == dimension(second.minimum().x(), second.maximum().x())
                && dimension(first.minimum().y(), first.maximum().y()) == dimension(second.minimum().y(), second.maximum().y())
                && dimension(first.minimum().z(), first.maximum().z()) == dimension(second.minimum().z(), second.maximum().z());
    }

    private static int dimension(int minimum, int maximum) {
        return maximum - minimum + 1;
    }

    private static Throwable root(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = root(throwable);
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record BlockWork(long blocks, String provider) { }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
