package site.mcrelicworld.relicprison.mining;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.MiningService;
import site.mcrelicworld.relicprison.api.event.RelicMineBlockProcessEvent;
import site.mcrelicworld.relicprison.api.model.BlockPosition;
import site.mcrelicworld.relicprison.api.model.SellResult;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.logging.LogCategory;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.mining.provider.CustomBlockProvider;
import site.mcrelicworld.relicprison.mining.provider.ItemsAdderCustomBlockProvider;
import site.mcrelicworld.relicprison.reward.RewardLedgerService;
import site.mcrelicworld.relicprison.reward.RewardComponentState;
import site.mcrelicworld.relicprison.selling.SellServiceImpl;
import site.mcrelicworld.relicprison.selling.SellSummaryService;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class MiningServiceImpl implements MiningService {
    private final RelicPrisonPlugin plugin;
    private final MiningConfigRepository configs;
    private final SellServiceImpl selling;
    private final SellSummaryService summaries;
    private final ToolDurabilityService durability;
    private final CustomBlockProvider customBlocks;
    private final BulkMiningTransactionRepository transactions;
    private final BulkMiningCommitRepository commits;
    private final LongAdder processed = new LongAdder();
    private final AtomicInteger active = new AtomicInteger();
    private final Map<UUID, RateWindow> playerRates = new ConcurrentHashMap<>();
    private final Map<UUID, Map<WorldBlockKey, Long>> recentCoordinates = new ConcurrentHashMap<>();
    private final Set<String> activeTransactions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> lastExperienceAwarded = new ConcurrentHashMap<>();
    private final AtomicInteger globalBlocksThisTick = new AtomicInteger();
    private final Enchantment fortune;
    private final NamespacedKey bulkTransactionKey;
    private volatile CustomDropCatalog customDrops;
    private int tickResetTask = -1;
    private int recoveryTask = -1;

    public MiningServiceImpl(RelicPrisonPlugin plugin, MiningConfigRepository configs,
                             SellServiceImpl selling, SellSummaryService summaries,
                             ToolDurabilityService durability) {
        this.plugin = plugin; this.configs = configs; this.selling = selling; this.summaries = summaries; this.durability = durability;
        this.customBlocks = new ItemsAdderCustomBlockProvider(plugin.itemsAdder());
        this.transactions = new BulkMiningTransactionRepository(plugin.database());
        this.commits = new BulkMiningCommitRepository(plugin.database());
        this.fortune = Enchantment.getByKey(NamespacedKey.minecraft("fortune"));
        this.bulkTransactionKey = new NamespacedKey(plugin, "bulk_transaction");
    }

    public void initialize() throws Exception {
        customDrops = CustomDropCatalog.load(plugin);
        tickResetTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> globalBlocksThisTick.set(0), 1L, 1L);
        recoverIncompleteBulkTransactions();
        recoveryTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
                this::recoverIncompleteBulkTransactions, 100L, 100L);
    }
    public CustomDropCatalog previewCustomDrops() throws Exception { return CustomDropCatalog.load(plugin); }
    public void applyCustomDrops(CustomDropCatalog next) { customDrops = next; }
    public void reloadCustomDrops() throws Exception { applyCustomDrops(previewCustomDrops()); }
    public CustomDropCatalog customDrops() { return customDrops; }
    public void shutdown() {
        if (tickResetTask != -1) Bukkit.getScheduler().cancelTask(tickResetTask);
        if (recoveryTask != -1) Bukkit.getScheduler().cancelTask(recoveryTask);
    }

    public NormalMiningPreparation prepareNormal(Player player, Block block, int vanillaXp, String source) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Mining must run on the server thread");
        MineDefinition mine = findMine(block);
        if (mine == null || plugin.config().snapshot().isExcludedWorld(block.getWorld().getName())) {
            return NormalMiningPreparation.notHandled();
        }
        if (!plugin.mineAccess().canMine(player.getUniqueId(), mine.id()) || plugin.mineResets().isResetting(mine.id())) {
            return NormalMiningPreparation.cancelled("Mine is locked or resetting");
        }
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(player.getUniqueId()).orElse(null);
        if (profile == null) return NormalMiningPreparation.cancelled("Profile is still loading");
        if (!durability.canUse(player, player.getInventory().getItemInMainHand())) {
            return NormalMiningPreparation.cancelled("Tool protected");
        }
        boolean ownDrops = ownsDrops(profile);
        if (!ownDrops) {
            boolean directXp = plugin.config().snapshot().features().miningXp();
            return NormalMiningPreparation.trackedOnly(player.getUniqueId(), block, mine.id(), block.getType(),
                    vanillaXp, source, directXp);
        }
        return NormalMiningPreparation.prepared(player.getUniqueId(), block, mine.id(), block.getType(),
                vanillaXp, source, true);
    }

    public ProcessResult processNormal(Player player, Block block, int vanillaXp, String source) {
        NormalMiningPreparation preparation = prepareNormal(player, block, vanillaXp, source);
        if (preparation.cancel()) return ProcessResult.cancelled(preparation.error());
        if (!preparation.prepared()) return ProcessResult.notHandled();
        return commitNormal(player, block, preparation);
    }

    public ProcessResult commitNormal(Player player, Block block, NormalMiningPreparation preparation) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Mining must run on the server thread");
        if (preparation == null || !preparation.prepared()) return ProcessResult.notHandled();
        if (!player.getUniqueId().equals(preparation.playerId())) return ProcessResult.cancelled("Player changed");
        if (!block.getWorld().getUID().equals(preparation.worldId())
                || block.getX() != preparation.x()
                || block.getY() != preparation.y()
                || block.getZ() != preparation.z()) {
            return ProcessResult.cancelled("Block coordinate changed before commit");
        }
        if (block.getType().isAir() || block.getType() != preparation.material()) {
            return ProcessResult.cancelled("Block changed before commit");
        }
        MineDefinition mine = plugin.mineService().findMine(preparation.mineId()).orElse(null);
        if (mine == null || !mine.bounds().contains(block.getX(), block.getY(), block.getZ())) {
            return ProcessResult.cancelled("Mine changed before commit");
        }
        if (!plugin.mineAccess().canMine(player.getUniqueId(), mine.id()) || plugin.mineResets().isResetting(mine.id())) {
            return ProcessResult.cancelled("Mine is locked or resetting");
        }
        WorldBlockKey key = new WorldBlockKey(preparation.worldId(), PackedBlockKey.pack(preparation.x(),
                preparation.y(), preparation.z()));
        if (!claimCoordinate(player.getUniqueId(), key)) return ProcessResult.cancelled("Duplicate block event");
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(player.getUniqueId()).orElse(null);
        if (profile == null) return ProcessResult.cancelled("Profile is still loading");
        if (!durability.canUse(player, player.getInventory().getItemInMainHand())) return ProcessResult.cancelled("Tool protected");
        if (!preparation.ownDrops()) {
            return ProcessResult.trackedOnly(preparation.suppressVanillaXp());
        }
        long started = System.nanoTime();
        active.incrementAndGet();
        try {
            RelicMineBlockProcessEvent apiEvent = new RelicMineBlockProcessEvent(player.getUniqueId(), mine.id(),
                    new BlockPosition(block.getX(), block.getY(), block.getZ()), preparation.source());
            Bukkit.getPluginManager().callEvent(apiEvent);
            if (apiEvent.isCancelled()) return ProcessResult.cancelled("Block processing cancelled");
            Material original = preparation.material();
            DropCalculation calculation = calculateDrops(player, block, original, profile, mine, false);
            List<ItemStack> drops = calculation.drops();
            boolean autoBlockApplied = false;
            if (autoSmeltEnabled(profile)) {
                drops = DropTransformer.smelt(drops, configs.config().smeltConversions());
            }
            if (autoBlockEnabled(profile)) {
                if (configs.config().autoBlockUseInventory() && autoPickupEnabled(profile)
                        && !plugin.config().snapshot().features().autoSell()) {
                    InventoryAutoBlockConverter.Result converted = InventoryAutoBlockConverter.convert(
                            player.getInventory(), drops, configs.config().blockConversions());
                    if (converted.successful()) {
                        autoBlockApplied = totalItems(drops) > 0;
                        drops = List.of();
                    }
                    else if (configs.config().overflowMode() == MiningConfig.OverflowMode.CANCEL) {
                        return ProcessResult.cancelled(converted.error());
                    } else {
                        drops = DropTransformer.block(drops, configs.config().blockConversions());
                    }
                } else {
                    List<ItemStack> beforeBlock = drops;
                    drops = DropTransformer.block(drops, configs.config().blockConversions());
                    autoBlockApplied = !sameItems(beforeBlock, drops);
                }
            }
            drops = DropTransformer.consolidate(drops);
            if (configs.config().overflowMode() == MiningConfig.OverflowMode.CANCEL
                    && autoPickupEnabled(profile)
                    && !canFit(player.getInventory(), drops)) {
                plugin.messages().send(player, "inventory-full", Map.of());
                return ProcessResult.cancelled("Inventory full");
            }
            Delivery delivery = deliver(player, profile, drops, autoBlockApplied);
            if (!delivery.success()) return ProcessResult.cancelled(delivery.error());
            customDrops.executeCommands(player, calculation.commands(), 1);
            plugin.invalidatePlaceholderCache(player.getUniqueId());
            if (plugin.config().snapshot().features().miningXp()) {
                awardExperience(player, calculateExperience(player, profile, original, mine.id(),
                        preparation.vanillaXp(), false));
            }
            Map<String, Integer> customIds = customBlocks.blockId(block).map(id -> Map.of(id, 1)).orElse(Map.of());
            recordMiningStatistics(player.getUniqueId(), mine.id(), Map.of(original, 1), customIds, false,
                    delivery.autoSellUsed() ? 1 : 0, delivery.autoPickupUsed() ? 1 : 0,
                    delivery.autoBlockUsed() ? 1 : 0, delivery.fallbackDroppedItems());
            record(player.getUniqueId(), mine.id(), 1);
            handleBlockEvents(player, profile, Set.of(mine.id()), Map.of(original, 1), customIds, 1, false);
            return ProcessResult.handled(1);
        } finally {
            plugin.performanceMetrics().recordNanos("mining.normal", System.nanoTime() - started);
            active.decrementAndGet();
        }
    }

    public void trackVanilla(Player player, Block block) {
        MineDefinition mine = findMine(block);
        if (mine == null || plugin.config().snapshot().isExcludedWorld(block.getWorld().getName())) return;
        if (!plugin.mineAccess().canMine(player.getUniqueId(), mine.id()) || plugin.mineResets().isResetting(mine.id())) return;
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(player.getUniqueId()).orElse(null);
        if (profile != null && plugin.config().snapshot().features().miningXp()) {
            awardExperience(player, calculateExperience(player, profile, block.getType(), mine.id(), 0, false));
        }
        Map<String, Integer> customIds = customBlocks.blockId(block).map(id -> Map.of(id, 1)).orElse(Map.of());
        recordMiningStatistics(player.getUniqueId(), mine.id(), Map.of(block.getType(), 1), customIds,
                false, 0L, 0L, 0L);
        record(player.getUniqueId(), mine.id(), 1);
    }

    public BulkResult processBulk(Player player, Collection<Block> input, String source) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Bulk mining must run on the server thread");
        long started = System.nanoTime();
        String transactionId = UUID.randomUUID().toString();
        MiningConfig config = configs.config();
        if (!acquireRate(player.getUniqueId())) return BulkResult.failure("Bulk operation rate limit reached");
        if (input.size() > config.maximumBlocksPerOperation()) return BulkResult.failure("Bulk operation exceeds block limit");
        int globalRemaining = config.maximumGlobalBlocksPerTick() - globalBlocksThisTick.get();
        if (globalRemaining <= 0) return BulkResult.failure("Global bulk mining budget exhausted");
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(player.getUniqueId()).orElse(null);
        if (profile == null) return BulkResult.failure("Profile is still loading");
        if (!durability.canUse(player, player.getInventory().getItemInMainHand())) return BulkResult.failure("Tool protected");

        BulkPlan plan = prepareBulkPlan(transactionId, player, input, source, profile, globalRemaining, config);
        if (!plan.success()) return BulkResult.failure(plan.error());
        long now = System.currentTimeMillis();
        BulkMiningTransactionRecord record = new BulkMiningTransactionRecord(transactionId, player.getUniqueId(),
                source == null ? "UNKNOWN" : source, plan.mineId(), BulkMiningTransactionState.VALIDATED,
                plan.prepared().size(), 0, plan.commandPackageId(), plan.snapshots(), plan.rewardPayload(),
                now, now, 0, "");
        transactions.create(record).whenComplete((created, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !Boolean.TRUE.equals(created)) {
                plugin.structuredLogger().severe(LogCategory.MINING, "bulk-mining-not-started transaction="
                        + transactionId + " player=" + player.getUniqueId() + " mine=" + plan.mineId()
                        + " blocks=" + plan.prepared().size() + " failure="
                        + (error == null ? "duplicate transaction" : rootMessage(error)));
                return;
            }
            commitBulkTransaction(record, plan, started);
        }));
        return new BulkResult(true, plan.prepared().size(), "queued");
    }

    private BulkPlan prepareBulkPlan(String transactionId, Player player, Collection<Block> input, String source,
                                     PlayerProfile profile, int globalRemaining, MiningConfig config) {
        Set<WorldBlockKey> unique = new HashSet<>();
        List<BulkPrepared> prepared = new ArrayList<>();
        Map<String, Integer> mineCounts = new HashMap<>();
        Map<Material, Integer> materialCounts = new java.util.EnumMap<>(Material.class);
        Map<String, Map<Material, Integer>> mineMaterialCounts = new HashMap<>();
        Map<String, Integer> customDropCommands = new HashMap<>();
        int xpBase = 0;
        for (Block block : input) {
            if (prepared.size() >= globalRemaining || prepared.size() >= config.maximumBlocksPerOperation()) break;
            WorldBlockKey coordinate = new WorldBlockKey(block.getWorld().getUID(),
                    PackedBlockKey.pack(block.getX(), block.getY(), block.getZ()));
            if (!unique.add(coordinate) || block.getType().isAir()) continue;
            MineDefinition mine = findMine(block);
            if (mine == null || !plugin.mineAccess().canMine(player.getUniqueId(), mine.id())
                    || plugin.mineResets().isResetting(mine.id())) continue;
            RelicMineBlockProcessEvent event = new RelicMineBlockProcessEvent(player.getUniqueId(), mine.id(),
                    new BlockPosition(block.getX(), block.getY(), block.getZ()), source);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            Material original = block.getType();
            DropCalculation calculation = calculateDrops(player, block, original, profile, mine, true);
            java.util.Optional<String> customId = customBlocks.blockId(block);
            BulkBlockSnapshot snapshot = new BulkBlockSnapshot(block.getWorld().getUID(), block.getX(), block.getY(),
                    block.getZ(), original.name(), block.getBlockData().getAsString(), customId.orElse(""));
            prepared.add(new BulkPrepared(snapshot, mine.id(), original, block.getBlockData().clone(),
                    customId.orElse(""), calculation, customId.isPresent()));
            calculation.commands().forEach((command, amount) -> customDropCommands.merge(command, amount, Integer::sum));
            xpBase += baseExperience(original, mine.id(), 0);
            mineCounts.merge(mine.id(), 1, Integer::sum);
            materialCounts.merge(original, 1, Integer::sum);
            mineMaterialCounts.computeIfAbsent(mine.id(), ignored -> new java.util.EnumMap<>(Material.class))
                    .merge(original, 1, Integer::sum);
        }
        if (prepared.isEmpty()) return BulkPlan.fail("No eligible mine blocks");
        ItemStack[] inventorySnapshot = cloneContents(player.getInventory().getStorageContents());
        DeliveryPlan deliveryPlan = planDelivery(player, profile, prepared, inventorySnapshot);
        if (!deliveryPlan.success()) return BulkPlan.fail(deliveryPlan.error());
        List<String> renderedCommands = customDrops == null ? List.of()
                : customDrops.renderCommands(player, customDropCommands, prepared.size());
        return BulkPlan.ok(transactionId, firstMineId(prepared), prepared, inventorySnapshot, deliveryPlan,
                mineCounts, materialCounts, mineMaterialCounts, xpBase, renderedCommands,
                plugin.config().snapshot().features().miningXp());
    }

    private void commitBulkTransaction(BulkMiningTransactionRecord record, BulkPlan plan, long started) {
        active.incrementAndGet();
        activeTransactions.add(record.transactionId());
        Player player = Bukkit.getPlayer(record.playerId());
        if (player == null || !player.isOnline()) {
            transactions.markRecoverable(record.transactionId(), "player disconnected before mutation");
            active.decrementAndGet();
            activeTransactions.remove(record.transactionId());
            return;
        }
        List<BulkPrepared> consumed = new ArrayList<>();
        try {
            for (BulkPrepared preparedBlock : plan.prepared()) {
                Block block = blockFromSnapshot(preparedBlock.snapshot());
                if (block == null) throw new BulkMiningException("World is unavailable before consumption");
                if (block.getType() != preparedBlock.material()
                        || !block.getBlockData().getAsString().equals(preparedBlock.blockData().getAsString())) {
                    throw new BulkMiningException("Block changed before consumption");
                }
                plugin.advancedEnchantments().ignoreBlockEvent(block);
                boolean removed = preparedBlock.custom()
                        ? customBlocks.removeBlock(block.getLocation())
                        : consumeVanillaBlock(block, preparedBlock.material());
                if (!removed) throw new BulkMiningException("Unable to consume block");
                consumed.add(preparedBlock);
            }
            List<BulkPrepared> committedBlocks = List.copyOf(consumed);
            transactions.markState(record.transactionId(), BulkMiningTransactionState.BLOCKS_MUTATED,
                    consumed.size(), record.rewardPackageId(), "").whenComplete((persisted, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null || !Boolean.TRUE.equals(persisted)) {
                            rollbackFailedBulk(record, player, plan, committedBlocks, "persist-block-mutation",
                                    error == null ? "transaction state update affected no row" : rootMessage(error),
                                    started);
                            return;
                        }
                        transactions.markState(record.transactionId(), BulkMiningTransactionState.REWARD_DELIVERING,
                                committedBlocks.size(), record.rewardPackageId(), "")
                                .whenComplete((deliveryPersisted, deliveryError) ->
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (deliveryError != null || !Boolean.TRUE.equals(deliveryPersisted)) {
                                                rollbackFailedBulk(record, player, plan, committedBlocks,
                                                        "persist-reward-intent", deliveryError == null
                                                                ? "transaction state update affected no row"
                                                                : rootMessage(deliveryError), started);
                                                return;
                                            }
                                            deliverBulkAfterMutation(record, plan, player, committedBlocks, started);
                                        }));
                    }));
        } catch (RuntimeException ex) {
            rollbackFailedBulk(record, player, plan, consumed, "consume-blocks", rootMessage(ex), started);
        }
    }

    private void deliverBulkAfterMutation(BulkMiningTransactionRecord record, BulkPlan plan, Player player,
                                          List<BulkPrepared> consumed, long started) {
        Delivery delivery = executeDeliveryPlan(player, plan.deliveryPlan(), plan.inventorySnapshot(),
                record.transactionId());
        if (!delivery.success()) {
            rollbackFailedBulk(record, player, plan, consumed, "deliver-rewards", delivery.error(), started);
            return;
        }
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(player.getUniqueId()).orElse(null);
        int experience = plugin.config().snapshot().features().miningXp()
                ? applyExperienceMultipliers(player, profile, plan.xpBase(), true) : 0;
        BulkMiningCommittedResult committed = committedResult(plan, delivery, experience);
        String packageId = committed.moneyEarned().signum() > 0 || committed.experience() > 0
                || !plan.renderedCommands().isEmpty() ? plan.commandPackageId() : "";
        BulkMiningTransactionRecord deliveredRecord = new BulkMiningTransactionRecord(record.transactionId(),
                record.playerId(), record.source(), record.mineId(), BulkMiningTransactionState.REWARD_DELIVERED,
                record.blockCount(), consumed.size(), packageId, record.blockSnapshots(), record.rewardPayload(),
                committed.encode(), delivery.droppedEntityIds(), record.createdAt(), System.currentTimeMillis(),
                record.recoveryAttempts(), "");
        transactions.markRewardDelivered(record.transactionId(), consumed.size(), packageId,
                record.rewardPayload(), committed.encode(), delivery.droppedEntityIds()).whenComplete((persisted, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || !Boolean.TRUE.equals(persisted)) {
                        removeDroppedItems(record.transactionId(), delivery.droppedEntityIds());
                        rollbackFailedBulk(record, player, plan, consumed, "persist-reward-delivery",
                                error == null ? "transaction state update affected no row" : rootMessage(error),
                                started);
                        return;
                    }
                    if (!packageId.isBlank()) {
                        createBulkRewardPackage(deliveredRecord, plan, player, consumed, delivery, committed, started);
                    } else {
                        finishBulkSuccess(deliveredRecord, plan, player, consumed.size(), delivery, committed, started);
                    }
                }));
    }

    private DropCalculation calculateDrops(Player player, Block block, Material original, PlayerProfile profile,
                                           MineDefinition mine, boolean bulk) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        java.util.Optional<String> customBlockId = plugin.config().snapshot().features().itemsAdder()
                ? customBlocks.blockId(block) : java.util.Optional.empty();
        String blockKey = customBlockId.orElse(original.name().toLowerCase(java.util.Locale.ROOT));
        CustomDropCatalog catalog = customDrops;
        if (plugin.config().snapshot().features().customBlockDrops() && catalog != null && catalog.hasRule(blockKey)) {
            CustomDropCatalog.DropEvaluation evaluation = catalog.evaluate(new CustomDropCatalog.DropContext(
                    player, profile, mine == null ? null : mine.id(), bulk ? "bulk" : "normal",
                    original, customBlockId.orElse(null), fortuneLevel(tool), bulk, 256, 64));
            if (evaluation.matched()) return new DropCalculation(new ArrayList<>(evaluation.items()), evaluation.commands());
        }
        if (customBlockId.isPresent()) return new DropCalculation(new ArrayList<>(customBlocks.blockLoot(block, tool)), Map.of());
        Collection<ItemStack> base;
        if (plugin.config().snapshot().features().fortune() && fortune != null && tool.getEnchantmentLevel(fortune) > 0) {
            ItemStack clean = tool.clone();
            int level = clean.getEnchantmentLevel(fortune);
            clean.removeEnchantment(fortune);
            base = block.getDrops(clean, (Entity) player);
            return new DropCalculation(new ArrayList<>(FortuneCalculator.apply(original, base, level,
                    configs.config().maximumFortuneLevel(), configs.config().fortuneMultiplier(),
                    configs.config().fortuneExcluded())), Map.of());
        }
        base = block.getDrops(tool, (Entity) player);
        return new DropCalculation(new ArrayList<>(base), Map.of());
    }

    private int fortuneLevel(ItemStack tool) {
        return fortune == null || tool == null ? 0 : Math.min(configs.config().maximumFortuneLevel(),
                Math.max(0, tool.getEnchantmentLevel(fortune)));
    }

    private Delivery deliver(Player player, PlayerProfile profile, List<ItemStack> input,
                             boolean autoBlockApplied) {
        List<ItemStack> drops = input;
        BigDecimal sold = BigDecimal.ZERO;
        long soldItems = 0L;
        if (plugin.config().snapshot().features().autoSell()) {
            SellServiceImpl.DropPartition partition = selling.partition(drops);
            if (!partition.sellable().isEmpty()) {
                SellResult result = selling.sellDrops(player, partition.sellable(), true);
                if (result.success()) {
                    summaries.add(player.getUniqueId(), result.finalValue());
                    sold = result.finalValue();
                    soldItems = result.itemCount();
                }
                else return Delivery.fail(result.error());
            }
            drops = partition.unsellable();
            if (drops.isEmpty()) return Delivery.ok(sold, soldItems, List.of(), soldItems > 0, false,
                    autoBlockApplied, 0L);
        }
        if (autoPickupEnabled(profile)) {
            ItemStack[] before = cloneContents(player.getInventory().getStorageContents());
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(drops.toArray(ItemStack[]::new));
            boolean pickedUp = totalItems(drops) > totalItems(overflow.values());
            if (overflow.isEmpty()) return Delivery.ok(sold, soldItems, List.of(), soldItems > 0, pickedUp,
                    autoBlockApplied, 0L);
            List<ItemStack> remaining = List.copyOf(overflow.values());
            if (configs.config().overflowMode() == MiningConfig.OverflowMode.SELL) {
                SellServiceImpl.DropPartition partition = selling.partition(remaining);
                if (!partition.sellable().isEmpty()) {
                    SellResult result = selling.sellDrops(player, partition.sellable(), true);
                    if (result.success()) {
                        summaries.add(player.getUniqueId(), result.finalValue());
                        sold = sold.add(result.finalValue());
                        soldItems += result.itemCount();
                    }
                    else partition = new SellServiceImpl.DropPartition(List.of(), remaining);
                }
                partition.unsellable().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                return Delivery.ok(sold, soldItems, List.of(), soldItems > 0, false, autoBlockApplied,
                        totalItems(partition.unsellable()));
            }
            if (configs.config().overflowMode() == MiningConfig.OverflowMode.CANCEL) {
                player.getInventory().setStorageContents(before);
                return Delivery.fail("Inventory full");
            }
            List<UUID> dropped = dropItems(player, remaining, null);
            plugin.messages().send(player, "inventory-overflow-dropped", Map.of());
            return Delivery.ok(sold, soldItems, dropped, soldItems > 0, false, autoBlockApplied,
                    totalItems(remaining));
        }
        List<UUID> dropped = dropItems(player, drops, null);
        return Delivery.ok(sold, soldItems, dropped, soldItems > 0, false, autoBlockApplied,
                totalItems(drops));
    }

    private DeliveryPlan planDelivery(Player player, PlayerProfile profile, List<BulkPrepared> prepared,
                                      ItemStack[] inventorySnapshot) {
        MiningConfig config = configs.config();
        boolean inventoryAutoBlock = config.autoBlockUseInventory() && autoBlockEnabled(profile)
                && autoPickupEnabled(profile) && !plugin.config().snapshot().features().autoSell();
        List<ItemStack> autoBlockSource = new ArrayList<>();
        List<RoutedItem> sellable = new ArrayList<>();
        List<RoutedItem> remaining = new ArrayList<>();
        Set<Integer> autoBlockBlocks = new HashSet<>();
        Set<Integer> inventoryPickupBlocks = new HashSet<>();
        for (int index = 0; index < prepared.size(); index++) {
            List<ItemStack> drops = DropTransformer.consolidate(prepared.get(index).calculation().drops());
            if (autoSmeltEnabled(profile)) drops = DropTransformer.smelt(drops, config.smeltConversions());
            if (inventoryAutoBlock) {
                autoBlockSource.addAll(drops);
                if (drops.stream().anyMatch(item -> !item.hasItemMeta()
                        && config.blockConversions().containsKey(item.getType()))) autoBlockBlocks.add(index);
                if (!drops.isEmpty()) inventoryPickupBlocks.add(index);
                continue;
            }
            if (autoBlockEnabled(profile)) {
                List<ItemStack> beforeBlock = drops;
                drops = DropTransformer.block(drops, config.blockConversions());
                if (!sameItems(beforeBlock, drops)) autoBlockBlocks.add(index);
            }
            if (plugin.config().snapshot().features().autoSell()) {
                SellServiceImpl.DropPartition partition = selling.partition(drops);
                for (ItemStack item : partition.sellable()) sellable.add(new RoutedItem(index, item));
                drops = partition.unsellable();
            }
            for (ItemStack item : drops) remaining.add(new RoutedItem(index, item));
        }
        if (inventoryAutoBlock) {
            InventoryAutoBlockConverter.Conversion conversion = InventoryAutoBlockConverter.plan(
                    inventorySnapshot, autoBlockSource, config.blockConversions());
            if (!fitsStorage(conversion.keptInventory(), conversion.output(), inventorySnapshot.length)) {
                return DeliveryPlan.fail("Inventory cannot fit AutoBlock conversion output");
            }
        }
        if (!sellable.isEmpty()) {
            List<ItemStack> sellableItems = sellable.stream().map(RoutedItem::item).toList();
            if (plugin.economy() == null || !plugin.economy().connected()) {
                return DeliveryPlan.fail("Vault economy is unavailable");
            }
            SellResult sale = selling.quoteDrops(player, sellableItems);
            if (!sale.success()) return DeliveryPlan.fail(sale.error());
            return finishDeliveryPlan(profile, inventorySnapshot, config, sellable, remaining,
                    inventoryAutoBlock, autoBlockSource, autoBlockBlocks, inventoryPickupBlocks,
                    sale.finalValue(), sale.itemCount());
        }
        return finishDeliveryPlan(profile, inventorySnapshot, config, sellable, remaining,
                inventoryAutoBlock, autoBlockSource, autoBlockBlocks, inventoryPickupBlocks,
                BigDecimal.ZERO, 0L);
    }

    private DeliveryPlan finishDeliveryPlan(PlayerProfile profile, ItemStack[] inventorySnapshot,
                                            MiningConfig config, List<RoutedItem> sellable,
                                            List<RoutedItem> remaining, boolean inventoryAutoBlock,
                                            List<ItemStack> autoBlockSource, Set<Integer> autoBlockBlocks,
                                            Set<Integer> inventoryPickupBlocks, BigDecimal saleValue,
                                            long saleItems) {
        List<ItemStack> remainingItems = remaining.stream().map(RoutedItem::item).toList();
        if (config.overflowMode() == MiningConfig.OverflowMode.CANCEL
                && autoPickupEnabled(profile) && !canFit(inventorySnapshot, remainingItems)) {
            return DeliveryPlan.fail("Inventory full");
        }
        return DeliveryPlan.ok(sellable, remaining, inventoryAutoBlock, autoBlockSource, autoBlockBlocks,
                inventoryPickupBlocks, saleValue, saleItems);
    }

    private Delivery executeDeliveryPlan(Player player, DeliveryPlan plan, ItemStack[] inventorySnapshot,
                                         String transactionId) {
        BigDecimal sold = BigDecimal.ZERO;
        long saleItems = 0L;
        List<UUID> dropped = new ArrayList<>();
        Map<Integer, MutableBlockRoute> routes = new HashMap<>();
        for (Integer index : plan.autoBlockBlocks()) route(routes, index).autoBlock = true;
        for (Integer index : plan.inventoryPickupBlocks()) route(routes, index).pickupMarker = true;
        try {
            if (plan.inventoryAutoBlock()) {
                if (!sameContents(player.getInventory().getStorageContents(), inventorySnapshot)) {
                    return Delivery.fail("Inventory changed before AutoBlock delivery");
                }
                InventoryAutoBlockConverter.Result converted = InventoryAutoBlockConverter.convert(
                        player.getInventory(), plan.autoBlockSource(), configs.config().blockConversions());
                if (!converted.successful()) return Delivery.fail(converted.error());
            }
            if (!plan.sellable().isEmpty()) {
                sold = plan.saleValue();
                saleItems = plan.saleItems();
                for (RoutedItem item : plan.sellable()) route(routes, item.blockIndex()).sold += item.item().getAmount();
            }
            if (plan.remaining().isEmpty()) return Delivery.bulk(sold, saleItems, dropped, routes);
            if (autoPickupEnabled(plugin.playerProfiles().cachedProfile(player.getUniqueId()).orElse(null))) {
                if (!sameContents(player.getInventory().getStorageContents(), inventorySnapshot)
                        && !plan.inventoryAutoBlock()) {
                    return Delivery.fail("Inventory changed before reward delivery");
                }
                List<ItemStack> tagged = new ArrayList<>();
                for (int index = 0; index < plan.remaining().size(); index++) {
                    tagged.add(tagBulkReward(plan.remaining().get(index).item(),
                            rewardComponentId(transactionId, "inventory", index)));
                }
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(tagged.toArray(ItemStack[]::new));
                for (int index = 0; index < plan.remaining().size(); index++) {
                    RoutedItem routed = plan.remaining().get(index);
                    int overflowAmount = overflow.containsKey(index) ? overflow.get(index).getAmount() : 0;
                    route(routes, routed.blockIndex()).picked += routed.item().getAmount() - overflowAmount;
                    route(routes, routed.blockIndex()).dropped += overflowAmount;
                }
                if (overflow.isEmpty()) return Delivery.bulk(sold, saleItems, dropped, routes);
                if (configs.config().overflowMode() == MiningConfig.OverflowMode.CANCEL) {
                    player.getInventory().setStorageContents(inventorySnapshot);
                    return Delivery.fail("Inventory full");
                }
                dropped.addAll(dropItems(player, overflow.values(), transactionId));
                return Delivery.bulk(sold, saleItems, dropped, routes);
            }
            List<ItemStack> remainingItems = new ArrayList<>();
            for (int index = 0; index < plan.remaining().size(); index++) {
                remainingItems.add(tagBulkReward(plan.remaining().get(index).item(),
                        rewardComponentId(transactionId, "drop", index)));
            }
            for (RoutedItem item : plan.remaining()) route(routes, item.blockIndex()).dropped += item.item().getAmount();
            dropped.addAll(dropItems(player, remainingItems, transactionId));
            return Delivery.bulk(sold, saleItems, dropped, routes);
        } catch (RuntimeException ex) {
            removeDroppedItems(transactionId, dropped);
            return Delivery.fail(rootMessage(ex));
        }
    }

    private static MutableBlockRoute route(Map<Integer, MutableBlockRoute> routes, int blockIndex) {
        return routes.computeIfAbsent(blockIndex, ignored -> new MutableBlockRoute());
    }

    private RollbackResult rollbackBulk(Player player, List<BulkPrepared> consumed, ItemStack[] inventorySnapshot) {
        return rollbackBulk(player, consumed, inventorySnapshot, "", List.of());
    }

    private RollbackResult rollbackBulk(Player player, List<BulkPrepared> consumed, ItemStack[] inventorySnapshot,
                                        String transactionId, List<UUID> droppedEntityIds) {
        int restored = 0;
        int failed = 0;
        for (int index = consumed.size() - 1; index >= 0; index--) {
            BulkPrepared preparedBlock = consumed.get(index);
            boolean ok;
            try {
                Block block = blockFromSnapshot(preparedBlock.snapshot());
                ok = preparedBlock.custom()
                        ? block != null && customBlocks.restoreBlock(block.getLocation(), preparedBlock.customId(),
                        preparedBlock.blockData())
                        : block != null && restoreVanillaBlock(block, preparedBlock.blockData());
            } catch (RuntimeException ex) {
                ok = false;
            }
            if (ok) restored++;
            else failed++;
        }
        try {
            player.getInventory().setStorageContents(inventorySnapshot);
        } catch (RuntimeException ex) {
            failed++;
        }
        removeDroppedItems(transactionId, droppedEntityIds);
        return new RollbackResult(restored, failed);
    }

    private void rollbackFailedBulk(BulkMiningTransactionRecord record, Player player, BulkPlan plan,
                                    List<BulkPrepared> consumed, String stage, String failure, long started) {
        rollbackFailedBulk(record, player, plan, consumed, stage, failure, started, false);
    }

    private void rollbackFailedBulk(BulkMiningTransactionRecord record, Player player, BulkPlan plan,
                                    List<BulkPrepared> consumed, String stage, String failure, long started,
                                    boolean forceRecoverable) {
        transactions.markState(record.transactionId(), BulkMiningTransactionState.ROLLING_BACK,
                consumed.size(), record.rewardPackageId(), failure).whenComplete((persisted, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> performBulkRollback(record, player, plan, consumed,
                        stage, error == null ? failure : failure + "; state-persistence=" + rootMessage(error),
                        started, forceRecoverable || error != null || !Boolean.TRUE.equals(persisted))));
    }

    private void performBulkRollback(BulkMiningTransactionRecord record, Player player, BulkPlan plan,
                                     List<BulkPrepared> consumed, String stage, String failure, long started,
                                     boolean forceRecoverable) {
        RollbackResult rollback = rollbackBulk(player, consumed, plan.inventorySnapshot(), record.transactionId(),
                record.spawnedEntityIds());
        BulkMiningTransactionState state = rollback.failed() == 0 && !forceRecoverable
                ? BulkMiningTransactionState.ROLLED_BACK : BulkMiningTransactionState.FAILED_RECOVERABLE;
        transactions.markState(record.transactionId(), state, consumed.size(), record.rewardPackageId(),
                failure + "; rollback=" + rollback);
        logBulkFailure(UUID.fromString(record.transactionId()), player, record.mineId(), plan.prepared().size(),
                stage, failure, rollback);
        plugin.performanceMetrics().recordNanos("mining.bulk", System.nanoTime() - started);
        active.decrementAndGet();
        activeTransactions.remove(record.transactionId());
    }

    private void createBulkRewardPackage(BulkMiningTransactionRecord record, BulkPlan plan, Player player,
                                         List<BulkPrepared> consumed, Delivery delivery,
                                         BulkMiningCommittedResult committed, long started) {
        long now = System.currentTimeMillis();
        List<RewardLedgerService.ComponentDraft> components = new ArrayList<>();
        if (committed.moneyEarned().signum() > 0) {
            BigDecimal target = plugin.economy().balance(player).add(committed.moneyEarned());
            components.add(RewardLedgerService.ComponentDraft.moneyTarget("autosell-money",
                    committed.moneyEarned(), target, now));
        }
        if (committed.experience() > 0) {
            int target = BulkRewardTarget.targetExperience(player.getTotalExperience(), committed.experience());
            components.add(RewardLedgerService.ComponentDraft.experienceTarget("mining-xp",
                    committed.experience(), target, now));
        }
        for (int index = 0; index < plan.renderedCommands().size(); index++) {
            components.add(RewardLedgerService.ComponentDraft.consoleCommand("custom-drop-command-" + index,
                    plan.renderedCommands().get(index), now));
        }
        plugin.rewardLedger().createPackage(record.rewardPackageId(), "bulk-mining", record.transactionId(),
                player.getUniqueId(), committed.encode(), components).whenComplete((created, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.structuredLogger().severe(LogCategory.MINING, "bulk-mining-reward-package-failed"
                                + " transaction=" + record.transactionId() + " player=" + player.getUniqueId()
                                + " mine=" + record.mineId() + " package=" + record.rewardPackageId()
                                + " failure=" + rootMessage(error));
                        removeDroppedItems(record.transactionId(), delivery.droppedEntityIds());
                        rollbackFailedBulk(record, player, plan, consumed, "reward-package",
                                rootMessage(error), started);
                        return;
                    }
                    finishBulkSuccess(record, plan, player, consumed.size(), delivery, committed, started);
                }));
    }

    private void finishBulkSuccess(BulkMiningTransactionRecord record, BulkPlan plan, Player player,
                                   int consumed, Delivery delivery, BulkMiningCommittedResult committed,
                                   long started) {
        if (record.rewardPackageId().isBlank()) {
            applyCommittedBulk(record, committed, player, plan, consumed, started, true);
            return;
        }
        plugin.rewardLedger().packageById(record.rewardPackageId()).whenComplete((rewardPackage, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        keepBulkFinalizing(record, "reward package verification failed: " + rootMessage(error));
                    } else if (rewardPackage.isPresent()
                            && rewardPackage.get().state() == RewardComponentState.COMPLETED) {
                        applyCommittedBulk(record, committed, player, plan, consumed, started, true);
                        return;
                    } else {
                        if (rewardPackage.isPresent()
                                && (rewardPackage.get().state() == RewardComponentState.STAFF_REVIEW
                                || rewardPackage.get().state() == RewardComponentState.PARTIALLY_FAILED)) {
                            keepBulkFinalizing(record, "reward package incomplete: "
                                    + rewardPackage.get().failureSummary());
                        } else {
                            plugin.rewardLedger().deliverDueAsync();
                        }
                    }
                    plugin.performanceMetrics().recordNanos("mining.bulk", System.nanoTime() - started);
                    active.decrementAndGet();
                    activeTransactions.remove(record.transactionId());
                }));
    }

    private void applyCommittedBulk(BulkMiningTransactionRecord record, BulkMiningCommittedResult committed,
                                    Player player, BulkPlan plan, int consumed, long started,
                                    boolean activeOperation) {
        PlayerProfile latestProfile = player == null ? null
                : plugin.playerProfiles().cachedProfile(player.getUniqueId()).orElse(null);
        plugin.playerProfiles().flushDirty()
                .thenCompose(ignored -> plugin.statistics().flush())
                .thenCompose(ignored -> commits.apply(record.transactionId(), record.playerId(), committed))
                .whenComplete((applied, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        keepBulkFinalizing(record, "bulk committed-result failure: " + rootMessage(error));
                        plugin.structuredLogger().severe(LogCategory.MINING, "bulk-mining-finalize-failed transaction="
                                + record.transactionId() + " player=" + record.playerId() + " stage=database-commit"
                                + " failure=" + rootMessage(error));
                        if (activeOperation) {
                            active.decrementAndGet();
                            activeTransactions.remove(record.transactionId());
                        }
                        return;
                    }
                    if (Boolean.TRUE.equals(applied) && player != null) {
                        plugin.playerProfiles().reflectCommittedBulk(record.playerId(), committed);
                        if (plugin.statistics() != null) plugin.statistics().reflectCommittedBulk(record.playerId(), committed);
                        for (BulkMiningCommittedResult.MineResult mine : committed.mines()) {
                            plugin.mineResets().recordBroken(mine.mineId(), mine.blocks());
                        }
                        if (committed.moneyEarned().signum() > 0) summaries.add(record.playerId(), committed.moneyEarned());
                        processed.add(consumed);
                        globalBlocksThisTick.addAndGet(consumed);
                        if (plan != null) durability.damageBulk(player, player.getInventory().getItemInMainHand(), consumed);
                    }
                    if (plugin.gangs() != null) {
                        committedMaterials(committed).forEach((material, amount) -> plugin.gangs().recordContribution(
                                record.playerId(), site.mcrelicworld.relicprison.gang.GangConfig.ContributionType.BLOCKS,
                                BigDecimal.valueOf(amount), material.name().toLowerCase(java.util.Locale.ROOT),
                                "bulk-mining:" + record.transactionId() + ':' + material.name()).exceptionally(failure -> null));
                        if (committed.moneyEarned().signum() > 0) plugin.gangs().recordContribution(record.playerId(),
                                site.mcrelicworld.relicprison.gang.GangConfig.ContributionType.MONEY,
                                committed.moneyEarned(), "", "bulk-money:" + record.transactionId())
                                .exceptionally(failure -> null);
                    }
                    if (player != null) {
                        Set<String> mineIds = committed.mines().stream()
                                .map(BulkMiningCommittedResult.MineResult::mineId)
                                .collect(java.util.stream.Collectors.toUnmodifiableSet());
                        handleBlockEvents(player, plugin.playerProfiles().cachedProfile(record.playerId()).orElse(null),
                                mineIds, committedMaterials(committed), committedCustomBlocks(committed), consumed, true,
                                UUID.fromString(record.transactionId()));
                    }
                    transactions.markState(record.transactionId(), BulkMiningTransactionState.COMMITTED, consumed,
                            record.rewardPackageId(), "all deterministic effects completed");
                    if (player != null) clearBulkRewardTags(player, record.transactionId());
                    plugin.invalidatePlaceholderCache(record.playerId());
                    plugin.structuredLogger().info(LogCategory.MINING, "bulk-mining-finalized transaction="
                            + record.transactionId() + " player=" + record.playerId() + " package="
                            + record.rewardPackageId() + " databaseEffectApplied=" + applied);
                    if (activeOperation) {
                        plugin.performanceMetrics().recordNanos("mining.bulk", System.nanoTime() - started);
                        active.decrementAndGet();
                        activeTransactions.remove(record.transactionId());
                    }
                }));
    }

    private static Map<Material, Integer> committedMaterials(BulkMiningCommittedResult committed) {
        Map<Material, Integer> materials = new java.util.EnumMap<>(Material.class);
        for (BulkMiningCommittedResult.MineResult mine : committed.mines()) {
            mine.materials().forEach((name, amount) -> {
                Material material = Material.matchMaterial(name);
                if (material != null) materials.merge(material, amount, Integer::sum);
            });
        }
        return Map.copyOf(materials);
    }

    private void keepBulkFinalizing(BulkMiningTransactionRecord record, String failure) {
        BulkMiningTransactionState state = record.state() == BulkMiningTransactionState.FINALIZING
                ? BulkMiningTransactionState.FINALIZING : BulkMiningTransactionState.REWARD_DELIVERED;
        transactions.markState(record.transactionId(), state, record.consumedCount(), record.rewardPackageId(), failure);
        plugin.structuredLogger().warning(LogCategory.MINING, "bulk-mining-recovery-pending transaction="
                + record.transactionId() + " player=" + record.playerId() + " stage=finalize failure=" + failure);
    }

    private BulkMiningCommittedResult committedResult(BulkPlan plan, Delivery delivery, int experience) {
        BulkMiningDeliveryResult actual = delivery.actualResult();
        List<BulkMiningCommittedResult.MineResult> mines = new ArrayList<>();
        for (var entry : plan.mineMaterialCounts().entrySet()) {
            Map<String, Integer> materials = new HashMap<>();
            entry.getValue().forEach((material, amount) -> materials.put(material.name(), amount));
            Map<String, Integer> customIds = new HashMap<>();
            for (BulkPrepared prepared : plan.prepared()) {
                if (prepared.mineId().equals(entry.getKey()) && prepared.custom()) {
                    customIds.merge(prepared.customId(), 1, Integer::sum);
                }
            }
            long autoSellBlocks = 0L;
            long autoPickupBlocks = 0L;
            long autoBlockBlocks = 0L;
            long fallbackDroppedItems = 0L;
            for (int index = 0; index < plan.prepared().size(); index++) {
                if (!plan.prepared().get(index).mineId().equals(entry.getKey())) continue;
                MiningRouteOutcome route = actual.blockRoutes().get(index);
                if (route == null) continue;
                if (route.autoSellBlock()) autoSellBlocks++;
                if (route.autoPickupBlock()) autoPickupBlocks++;
                if (route.autoBlockApplied()) autoBlockBlocks++;
                fallbackDroppedItems += route.droppedItems();
            }
            mines.add(new BulkMiningCommittedResult.MineResult(entry.getKey(), materials, customIds,
                    autoSellBlocks, autoPickupBlocks, autoBlockBlocks, fallbackDroppedItems));
        }
        long now = System.currentTimeMillis();
        List<BulkMiningCommittedResult.Period> periods = plugin.statistics() == null ? List.of()
                : plugin.statistics().committedPeriods(now);
        return new BulkMiningCommittedResult(now, plan.prepared().size(), actual.itemsSold(), actual.moneyEarned(),
                experience, plugin.config().snapshot().features().mineAnalytics(), periods, mines);
    }

    private Block blockFromSnapshot(BulkBlockSnapshot snapshot) {
        org.bukkit.World world = Bukkit.getWorld(snapshot.worldId());
        return world == null ? null : world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
    }

    public void recoverIncompleteBulkTransactions() {
        transactions.incomplete(50).thenAccept(records -> {
            if (records.isEmpty()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (BulkMiningTransactionRecord record : records) recoverBulkTransaction(record);
            });
        }).exceptionally(error -> {
            plugin.getLogger().warning("Bulk mining recovery query failed: " + rootMessage(error));
            return null;
        });
    }

    private void recoverBulkTransaction(BulkMiningTransactionRecord record) {
        if (activeTransactions.contains(record.transactionId())) return;
        boolean anyPresent = false;
        boolean anyMissing = false;
        for (BulkBlockSnapshot snapshot : record.blockSnapshots()) {
            Block block = blockFromSnapshot(snapshot);
            if (block == null) {
                transactions.markRecoverable(record.transactionId(), "world unavailable during recovery");
                return;
            }
            Material expected = Material.matchMaterial(snapshot.material());
            boolean present = expected != null && block.getType() == expected
                    && block.getBlockData().getAsString().equals(snapshot.blockData());
            anyPresent |= present;
            anyMissing |= !present;
        }
        BulkMiningRecoveryDecision.Action action = BulkMiningRecoveryDecision.forState(record.state());
        if (action == BulkMiningRecoveryDecision.Action.ROLLBACK) {
            recoverByRollback(record);
            return;
        }
        if (action == BulkMiningRecoveryDecision.Action.COMPLETE) {
            recoverDeliveredBulk(record);
            return;
        }
        if (anyPresent) {
            removeDroppedItems(record.transactionId(), record.spawnedEntityIds());
            transactions.markState(record.transactionId(), BulkMiningTransactionState.ROLLED_BACK,
                    0, record.rewardPackageId(), "all blocks still present after restart");
            return;
        }
        recoverByRollback(record);
    }

    private void recoverByRollback(BulkMiningTransactionRecord record) {
        int restored = 0;
        int failed = 0;
        for (BulkBlockSnapshot snapshot : record.blockSnapshots()) {
            try {
                Block block = blockFromSnapshot(snapshot);
                if (block == null) {
                    failed++;
                    continue;
                }
                Material expected = Material.matchMaterial(snapshot.material());
                boolean alreadyPresent = expected != null && block.getType() == expected
                        && block.getBlockData().getAsString().equals(snapshot.blockData());
                boolean success = alreadyPresent || (!snapshot.customBlockId().isBlank()
                        ? customBlocks.restoreBlock(block.getLocation(), snapshot.customBlockId(),
                        Bukkit.createBlockData(snapshot.blockData()))
                        : restoreVanillaBlock(block, Bukkit.createBlockData(snapshot.blockData())));
                if (success) restored++;
                else failed++;
            } catch (RuntimeException error) {
                failed++;
            }
        }
        removeDroppedItems(record.transactionId(), record.spawnedEntityIds());
        Player player = Bukkit.getPlayer(record.playerId());
        if ((record.state() == BulkMiningTransactionState.REWARD_DELIVERING
                || record.state() == BulkMiningTransactionState.ROLLING_BACK) && player == null) {
            transactions.markState(record.transactionId(), BulkMiningTransactionState.ROLLING_BACK,
                    record.consumedCount(), record.rewardPackageId(),
                    "blocks restored; waiting for player to remove provisional inventory rewards");
            return;
        }
        if (player != null) removeBulkRewardItems(player, record.transactionId());
        BulkMiningTransactionState state = failed == 0 ? BulkMiningTransactionState.ROLLED_BACK
                : BulkMiningTransactionState.FAILED_RECOVERABLE;
        transactions.markState(record.transactionId(), state, record.consumedCount(), record.rewardPackageId(),
                "startup recovery rollback restored=" + restored + " failed=" + failed);
        plugin.structuredLogger().info(LogCategory.MINING, "bulk-mining-recovery transaction="
                + record.transactionId() + " player=" + record.playerId() + " result=" + state
                + " restored=" + restored + " failed=" + failed);
    }

    private void recoverDeliveredBulk(BulkMiningTransactionRecord record) {
        List<String> commands;
        BulkMiningCommittedResult committed;
        try {
            commands = decodeRewardPayload(record.rewardPayload());
            committed = BulkMiningCommittedResult.decode(record.commitPayload());
        } catch (IllegalArgumentException error) {
            keepBulkFinalizing(record, "invalid frozen bulk payload: " + rootMessage(error));
            return;
        }
        long now = System.currentTimeMillis();
        List<RewardLedgerService.ComponentDraft> components = new ArrayList<>();
        if (committed.moneyEarned().signum() > 0) {
            Player player = Bukkit.getPlayer(record.playerId());
            if (player == null || !player.isOnline()) {
                keepBulkFinalizing(record, "player offline while reconstructing bulk money target");
                return;
            }
            BigDecimal target = plugin.economy().balance(player).add(committed.moneyEarned());
            components.add(RewardLedgerService.ComponentDraft.moneyTarget("autosell-money",
                    committed.moneyEarned(), target, now));
        }
        if (committed.experience() > 0) {
            Player player = Bukkit.getPlayer(record.playerId());
            if (player == null || !player.isOnline()) {
                keepBulkFinalizing(record, "player offline while reconstructing bulk XP target");
                return;
            }
            components.add(RewardLedgerService.ComponentDraft.experienceTarget("mining-xp",
                    committed.experience(), BulkRewardTarget.targetExperience(player.getTotalExperience(),
                            committed.experience()), now));
        }
        for (int index = 0; index < commands.size(); index++) {
            components.add(RewardLedgerService.ComponentDraft.consoleCommand("custom-drop-command-" + index,
                    commands.get(index), now));
        }
        if (record.rewardPackageId().isBlank()) {
            recoverCommittedEffects(record, committed);
            return;
        }
        plugin.rewardLedger().createPackage(record.rewardPackageId(), "bulk-mining", record.transactionId(),
                record.playerId(), record.commitPayload(), components)
                .whenComplete((created, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        keepBulkFinalizing(record, "bulk reward package recovery failed: " + rootMessage(error));
                        return;
                    }
                    recoverCommittedEffects(record, committed);
                }));
    }

    private void recoverCommittedEffects(BulkMiningTransactionRecord record,
                                         BulkMiningCommittedResult committed) {
        Player player = Bukkit.getPlayer(record.playerId());
        if (record.rewardPackageId().isBlank()) {
            applyCommittedBulk(record, committed, player, null, record.consumedCount(), 0L, false);
            return;
        }
        plugin.rewardLedger().packageById(record.rewardPackageId()).whenComplete((rewardPackage, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        keepBulkFinalizing(record, "reward package verification failed: " + rootMessage(error));
                    } else if (rewardPackage.isPresent()
                            && rewardPackage.get().state() == RewardComponentState.COMPLETED) {
                        applyCommittedBulk(record, committed, player, null, record.consumedCount(), 0L, false);
                    } else if (rewardPackage.isPresent()
                            && (rewardPackage.get().state() == RewardComponentState.STAFF_REVIEW
                            || rewardPackage.get().state() == RewardComponentState.PARTIALLY_FAILED)) {
                        keepBulkFinalizing(record, "reward package incomplete: "
                                + rewardPackage.get().failureSummary());
                    } else {
                        plugin.rewardLedger().deliverDueAsync();
                    }
                }));
    }

    private void logBulkFailure(UUID operationId, Player player, String mineId, int affectedBlocks, String stage,
                                String failure, RollbackResult rollback) {
        plugin.structuredLogger().severe(LogCategory.MINING, "bulk-mining-failed transaction=" + operationId
                + " player=" + player.getUniqueId() + " mine=" + mineId + " blocks=" + affectedBlocks
                + " route=" + rewardRoute() + " stage=" + stage + " failure=" + failure
                + " rollback=restored:" + rollback.restored() + ",failed:" + rollback.failed());
    }

    private String rewardRoute() {
        var features = plugin.config().snapshot().features();
        return "autosell=" + features.autoSell() + ",autopickup=" + features.autoPickup()
                + ",autoblock=" + features.autoBlock();
    }

    private static String firstMineId(List<BulkPrepared> prepared) {
        return prepared.isEmpty() ? "unknown" : prepared.getFirst().mineId();
    }

    private static String encodeRewardPayload(List<String> renderedCommands) {
        if (renderedCommands == null || renderedCommands.isEmpty()) return "";
        return String.join("\n", renderedCommands.stream()
                .map(command -> Base64.getEncoder().encodeToString(
                        command.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .toList());
    }

    private static List<String> decodeRewardPayload(String payload) {
        if (payload == null || payload.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String line : payload.split("\\R")) {
            if (line.isBlank()) continue;
            result.add(new String(Base64.getDecoder().decode(line),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        return List.copyOf(result);
    }

    private static Map<String, Map<Material, Integer>> copyNested(Map<String, Map<Material, Integer>> input) {
        Map<String, Map<Material, Integer>> copy = new HashMap<>();
        input.forEach((mine, materials) -> copy.put(mine, Map.copyOf(materials)));
        return Map.copyOf(copy);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copy;
    }

    private static boolean sameContents(ItemStack[] current, ItemStack[] snapshot) {
        if (current.length != snapshot.length) return false;
        for (int index = 0; index < current.length; index++) {
            ItemStack left = current[index];
            ItemStack right = snapshot[index];
            if (left == null || left.getType().isAir()) {
                if (right != null && !right.getType().isAir()) return false;
            } else if (right == null || right.getType().isAir()) {
                return false;
            } else if (left.getAmount() != right.getAmount() || !left.isSimilar(right)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameItems(Collection<ItemStack> left, Collection<ItemStack> right) {
        List<ItemStack> first = DropTransformer.consolidate(left);
        List<ItemStack> second = new ArrayList<>(DropTransformer.consolidate(right));
        if (first.size() != second.size()) return false;
        for (ItemStack item : first) {
            int match = -1;
            for (int index = 0; index < second.size(); index++) {
                ItemStack candidate = second.get(index);
                if (item.getAmount() == candidate.getAmount() && item.isSimilar(candidate)) {
                    match = index;
                    break;
                }
            }
            if (match < 0) return false;
            second.remove(match);
        }
        return second.isEmpty();
    }

    private static long totalItems(Collection<ItemStack> items) {
        return items.stream().filter(java.util.Objects::nonNull).mapToLong(ItemStack::getAmount).sum();
    }

    private void record(UUID playerId, String mineId, int blocks) {
        plugin.mineResets().recordBroken(mineId, blocks);
        plugin.playerProfiles().recordBlocks(playerId, blocks);
        if (plugin.statistics() != null && plugin.config().snapshot().features().mineAnalytics()) plugin.statistics().recordBlocks(playerId, mineId, blocks);
        processed.add(blocks);
    }

    private void recordMiningStatistics(UUID playerId, String mineId, Map<Material, Integer> materials,
                                        Map<String, Integer> customBlocks, boolean bulk, long autoSellBlocks,
                                        long autoPickupBlocks, long autoBlockBlocks) {
        recordMiningStatistics(playerId, mineId, materials, customBlocks, bulk, autoSellBlocks,
                autoPickupBlocks, autoBlockBlocks, 0L);
    }

    private void recordMiningStatistics(UUID playerId, String mineId, Map<Material, Integer> materials,
                                        Map<String, Integer> customBlocks, boolean bulk, long autoSellBlocks,
                                        long autoPickupBlocks, long autoBlockBlocks, long fallbackDroppedItems) {
        if (plugin.statistics() == null) return;
        plugin.statistics().recordMining(playerId, mineId, materials, customBlocks, bulk, autoSellBlocks,
                autoPickupBlocks, autoBlockBlocks, fallbackDroppedItems);
        if (plugin.gangs() != null) {
            String operation = "normal-mining:" + UUID.randomUUID();
            materials.forEach((material, amount) -> plugin.gangs().recordContribution(playerId,
                    site.mcrelicworld.relicprison.gang.GangConfig.ContributionType.BLOCKS,
                    BigDecimal.valueOf(amount), material.name().toLowerCase(java.util.Locale.ROOT),
                    operation + ':' + material.name()).exceptionally(error -> null));
        }
    }

    private List<UUID> dropItems(Player player, Collection<ItemStack> items, String transactionId) {
        List<UUID> entityIds = new ArrayList<>();
        int index = 0;
        for (ItemStack stack : items) {
            ItemStack delivered = transactionId == null || transactionId.isBlank() ? stack
                    : tagBulkReward(stack, rewardComponentId(transactionId, "drop", index));
            Item entity = player.getWorld().dropItemNaturally(player.getLocation(), delivered);
            if (transactionId != null && !transactionId.isBlank()) {
                entity.getPersistentDataContainer().set(bulkTransactionKey, PersistentDataType.STRING,
                        rewardComponentId(transactionId, "drop", index));
            }
            entityIds.add(entity.getUniqueId());
            index++;
        }
        return List.copyOf(entityIds);
    }

    private ItemStack tagBulkReward(ItemStack original, String componentId) {
        ItemStack tagged = original.clone();
        var meta = tagged.getItemMeta();
        meta.getPersistentDataContainer().set(bulkTransactionKey, PersistentDataType.STRING, componentId);
        tagged.setItemMeta(meta);
        return tagged;
    }

    private void removeBulkRewardItems(Player player, String transactionId) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int index = 0; index < contents.length; index++) {
            ItemStack item = contents[index];
            if (item == null || !item.hasItemMeta()) continue;
            String owner = item.getItemMeta().getPersistentDataContainer().get(
                    bulkTransactionKey, PersistentDataType.STRING);
            if (belongsToTransaction(owner, transactionId)) {
                contents[index] = null;
                changed = true;
            }
        }
        if (changed) player.getInventory().setStorageContents(contents);
    }

    private void clearBulkRewardTags(Player player, String transactionId) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            if (item == null || !item.hasItemMeta()) continue;
            var meta = item.getItemMeta();
            String owner = meta.getPersistentDataContainer().get(bulkTransactionKey, PersistentDataType.STRING);
            if (!belongsToTransaction(owner, transactionId)) continue;
            meta.getPersistentDataContainer().remove(bulkTransactionKey);
            item.setItemMeta(meta);
            changed = true;
        }
        if (changed) player.getInventory().setStorageContents(contents);
    }

    private void removeDroppedItems(String transactionId, Collection<UUID> entityIds) {
        for (UUID entityId : entityIds) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof Item item) item.remove();
        }
        if (transactionId == null || transactionId.isBlank()) return;
        int scanned = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (++scanned > 10_000) return;
                String owner = item.getPersistentDataContainer().get(bulkTransactionKey, PersistentDataType.STRING);
                if (belongsToTransaction(owner, transactionId)) item.remove();
            }
        }
    }

    private static String rewardComponentId(String transactionId, String route, int index) {
        return transactionId + ':' + route + ':' + index;
    }

    private static boolean belongsToTransaction(String componentId, String transactionId) {
        return componentId != null && (componentId.equals(transactionId)
                || componentId.startsWith(transactionId + ':'));
    }

    private void handleBlockEvents(Player player, PlayerProfile profile, Set<String> mineIds,
                                   Map<Material, Integer> materials, int blocks, boolean bulk) {
        handleBlockEvents(player, profile, mineIds, materials, Map.of(), blocks, bulk, UUID.randomUUID());
    }

    private void handleBlockEvents(Player player, PlayerProfile profile, Set<String> mineIds,
                                   Map<Material, Integer> materials, Map<String, Integer> customBlockIds,
                                   int blocks, boolean bulk) {
        handleBlockEvents(player, profile, mineIds, materials, customBlockIds, blocks, bulk, UUID.randomUUID());
    }

    private void handleBlockEvents(Player player, PlayerProfile profile, Set<String> mineIds,
                                   Map<Material, Integer> materials, Map<String, Integer> customBlockIds,
                                   int blocks, boolean bulk, UUID operationId) {
        if (plugin.blockEvents() == null || !plugin.config().snapshot().features().blockEvents()) return;
        plugin.blockEvents().handle(new site.mcrelicworld.relicprison.blockevent.BlockEventService.MiningAction(
                operationId, player, profile, mineIds.stream().map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet()), Map.copyOf(materials),
                Map.copyOf(customBlockIds), blocks, bulk));
    }

    private static Map<String, Integer> committedCustomBlocks(BulkMiningCommittedResult committed) {
        Map<String, Integer> custom = new java.util.HashMap<>();
        for (BulkMiningCommittedResult.MineResult mine : committed.mines()) {
            mine.customBlocks().forEach((id, amount) -> custom.merge(id, amount, Integer::sum));
        }
        return Map.copyOf(custom);
    }

    private int calculateExperience(Player player, PlayerProfile profile, Material material, String mineId,
                                    int vanillaXp, boolean bulk) {
        return applyExperienceMultipliers(player, profile, baseExperience(material, mineId, vanillaXp), bulk);
    }

    private int baseExperience(Material material, String mineId, int vanillaXp) {
        MiningConfig config = configs.config();
        int materialXp = config.experienceByMaterial().getOrDefault(material, Math.max(0, vanillaXp));
        int mineXp = mineId == null ? 0 : config.experienceByMine().getOrDefault(mineId.toLowerCase(java.util.Locale.ROOT), 0);
        return Math.max(0, materialXp + mineXp);
    }

    private int applyExperienceMultipliers(Player player, PlayerProfile profile, int base, boolean bulk) {
        if (base <= 0) return 0;
        MiningConfig config = configs.config();
        double rank = profile == null ? 1.0D : config.rankExperienceMultipliers()
                .getOrDefault(profile.currentRank().toLowerCase(java.util.Locale.ROOT), 1.0D);
        double prestige = profile == null || profile.currentPrestige() == null ? 1.0D : config.prestigeExperienceMultipliers()
                .getOrDefault(profile.currentPrestige().toLowerCase(java.util.Locale.ROOT), 1.0D);
        double permission = 1.0D;
        for (var entry : config.permissionExperienceMultipliers().entrySet()) {
            if (player.hasPermission(entry.getKey())) permission = Math.max(permission, entry.getValue());
        }
        double multiplier = switch (config.experienceMultiplierMode()) {
            case MULTIPLICATIVE -> rank * prestige * permission;
            case ADDITIVE_BONUSES -> 1.0D + Math.max(0.0D, rank - 1.0D)
                    + Math.max(0.0D, prestige - 1.0D) + Math.max(0.0D, permission - 1.0D);
        };
        if (plugin.boosterService() != null) multiplier *= plugin.boosterService().gangMultiplier(player.getUniqueId(),
                site.mcrelicworld.relicprison.gang.GangBooster.Type.MINING_XP).doubleValue();
        if (bulk) multiplier *= config.bulkExperienceScaling();
        double raw = base * Math.max(0.0D, multiplier);
        int capped = raw >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(raw);
        int limit = config.maximumExperiencePerOperation();
        return limit <= 0 ? 0 : Math.max(0, Math.min(capped, limit));
    }

    private void awardExperience(Player player, int amount) {
        int safe = Math.max(0, amount);
        lastExperienceAwarded.put(player.getUniqueId(), safe);
        if (safe > 0) player.giveExp(safe);
    }

    public int lastExperienceAwarded(UUID playerId) {
        return lastExperienceAwarded.getOrDefault(playerId, 0);
    }

    private MineDefinition findMine(Block block) {
        return plugin.mineService().mineAt(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()).orElse(null);
    }

    private boolean autoPickupEnabled(PlayerProfile profile) {
        return plugin.config().snapshot().features().autoPickup();
    }

    private boolean autoSmeltEnabled(PlayerProfile profile) {
        return plugin.config().snapshot().features().autoSmelt();
    }

    private boolean autoBlockEnabled(PlayerProfile profile) {
        return plugin.config().snapshot().features().autoBlock();
    }

    private boolean ownsDrops(PlayerProfile profile) {
        return ownsDropsGlobally();
    }

    /** Server feature flags are authoritative; player profile toggles are retained only for data compatibility. */
    public boolean ownsDropsGlobally() {
        var features = plugin.config().snapshot().features();
        return features.autoSell() || features.autoPickup() || features.autoSmelt()
                || features.autoBlock() || features.fortune();
    }

    private boolean claimCoordinate(UUID playerId, WorldBlockKey key) {
        long now = System.currentTimeMillis();
        Map<WorldBlockKey, Long> map = recentCoordinates.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        Long previous = map.put(key, now);
        if (map.size() > 2048) map.entrySet().removeIf(entry -> now - entry.getValue() > 1000L);
        return previous == null || now - previous > 250L;
    }

    private boolean acquireRate(UUID playerId) {
        long second = System.currentTimeMillis() / 1000L;
        RateWindow window = playerRates.compute(playerId, (ignored, current) -> current == null || current.second != second
                ? new RateWindow(second, 1) : new RateWindow(second, current.count + 1));
        return window.count <= configs.config().maximumOperationsPerPlayerPerSecond();
    }

    private static boolean canFit(PlayerInventory inventory, List<ItemStack> items) {
        return canFit(inventory.getStorageContents(), items);
    }

    private static boolean canFit(ItemStack[] contents, List<ItemStack> items) {
        int freeSlots = 0;
        Map<Material, Integer> space = new HashMap<>();
        for (ItemStack current : contents) {
            if (current == null || current.getType().isAir()) freeSlots++;
            else if (!current.hasItemMeta()) space.merge(current.getType(), current.getMaxStackSize() - current.getAmount(), Integer::sum);
        }
        for (ItemStack item : items) {
            int remaining = item.getAmount();
            if (!item.hasItemMeta()) remaining -= Math.min(remaining, space.getOrDefault(item.getType(), 0));
            if (remaining <= 0) continue;
            int needed = (remaining + item.getMaxStackSize() - 1) / item.getMaxStackSize();
            freeSlots -= needed;
            if (freeSlots < 0) return false;
        }
        return true;
    }

    private static boolean fitsStorage(List<ItemStack> kept, List<ItemStack> output, int slots) {
        int required = 0;
        for (ItemStack item : DropTransformer.consolidate(kept)) {
            required += (item.getAmount() + item.getMaxStackSize() - 1) / item.getMaxStackSize();
        }
        for (ItemStack item : DropTransformer.consolidate(output)) {
            required += (item.getAmount() + item.getMaxStackSize() - 1) / item.getMaxStackSize();
        }
        return required <= slots;
    }

    private static boolean consumeVanillaBlock(Block block, Material expected) {
        if (block.getType() != expected || expected.isAir()) return false;
        block.setType(Material.AIR, false);
        return block.getType().isAir();
    }

    private static boolean restoreVanillaBlock(Block block, BlockData blockData) {
        if (blockData == null) return false;
        block.setBlockData(blockData, false);
        return block.getBlockData().getAsString().equals(blockData.getAsString());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @Override public long processedBlocks() { return processed.sum(); }
    @Override public long activeOperations() { return active.get(); }
    public int blocksProcessedThisTick() { return globalBlocksThisTick.get(); }

    private record RateWindow(long second, int count) {}
    private record WorldBlockKey(UUID world, long coordinate) {}
    private record DropCalculation(List<ItemStack> drops, Map<String, Integer> commands) {}
    private record BulkPrepared(BulkBlockSnapshot snapshot, String mineId, Material material, BlockData blockData, String customId,
                                DropCalculation calculation, boolean custom) {}
    private record BulkPlan(boolean success, String error, String transactionId, String mineId,
                            List<BulkPrepared> prepared, List<BulkBlockSnapshot> snapshots,
                            ItemStack[] inventorySnapshot, DeliveryPlan deliveryPlan,
                            Map<String, Integer> mineCounts, Map<Material, Integer> materialCounts,
                            Map<String, Map<Material, Integer>> mineMaterialCounts, int xpBase,
                            List<String> renderedCommands, String commandPackageId, String rewardPayload) {
        static BulkPlan ok(String transactionId, String mineId, List<BulkPrepared> prepared,
                           ItemStack[] inventorySnapshot, DeliveryPlan deliveryPlan,
                           Map<String, Integer> mineCounts, Map<Material, Integer> materialCounts,
                           Map<String, Map<Material, Integer>> mineMaterialCounts, int xpBase,
                           List<String> renderedCommands, boolean miningExperienceEnabled) {
            List<BulkBlockSnapshot> snapshots = prepared.stream().map(BulkPrepared::snapshot).toList();
            String packageId = renderedCommands.isEmpty() && (!miningExperienceEnabled || xpBase <= 0)
                    && deliveryPlan.saleValue().signum() <= 0
                    ? "" : transactionId + "-effects";
            return new BulkPlan(true, "", transactionId, mineId, List.copyOf(prepared), List.copyOf(snapshots),
                    cloneContents(inventorySnapshot), deliveryPlan, Map.copyOf(mineCounts), Map.copyOf(materialCounts),
                    copyNested(mineMaterialCounts), xpBase, List.copyOf(renderedCommands),
                    packageId, encodeRewardPayload(renderedCommands));
        }

        static BulkPlan fail(String error) {
            return new BulkPlan(false, error, "", "unknown", List.of(), List.of(), new ItemStack[0],
                    DeliveryPlan.fail(error), Map.of(), Map.of(), Map.of(),
                    0, List.of(), "", "");
        }
    }
    private record RoutedItem(int blockIndex, ItemStack item) { }
    private static final class MutableBlockRoute {
        private long sold;
        private long picked;
        private long dropped;
        private boolean autoBlock;
        private boolean pickupMarker;

        private MiningRouteOutcome freeze() {
            return new MiningRouteOutcome(sold, picked + (pickupMarker ? 1L : 0L), dropped, autoBlock);
        }
    }
    private record DeliveryPlan(boolean success, String error, List<RoutedItem> sellable,
                                List<RoutedItem> remaining, boolean inventoryAutoBlock,
                                List<ItemStack> autoBlockSource, Set<Integer> autoBlockBlocks,
                                Set<Integer> inventoryPickupBlocks, BigDecimal saleValue, long saleItems) {
        static DeliveryPlan ok(List<RoutedItem> sellable, List<RoutedItem> remaining, boolean inventoryAutoBlock,
                               List<ItemStack> autoBlockSource, Set<Integer> autoBlockBlocks,
                               Set<Integer> inventoryPickupBlocks, BigDecimal saleValue, long saleItems) {
            return new DeliveryPlan(true, "", List.copyOf(sellable), List.copyOf(remaining),
                    inventoryAutoBlock, List.copyOf(autoBlockSource), Set.copyOf(autoBlockBlocks),
                    Set.copyOf(inventoryPickupBlocks), saleValue == null ? BigDecimal.ZERO : saleValue,
                    Math.max(0L, saleItems));
        }
        static DeliveryPlan fail(String error) {
            return new DeliveryPlan(false, error, List.of(), List.of(), false, List.of(), Set.of(), Set.of(),
                    BigDecimal.ZERO, 0L);
        }
    }
    private record RollbackResult(int restored, int failed) {}
    private static final class BulkMiningException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private BulkMiningException(String message) { super(message); }
    }
    private record Delivery(boolean success, String error, BigDecimal sold, long saleItems,
                            List<UUID> droppedEntityIds, boolean autoSellUsed, boolean autoPickupUsed,
                            boolean autoBlockUsed, long fallbackDroppedItems,
                            Map<Integer, MiningRouteOutcome> blockRoutes) {
        Delivery {
            droppedEntityIds = List.copyOf(droppedEntityIds == null ? List.of() : droppedEntityIds);
            blockRoutes = Map.copyOf(blockRoutes == null ? Map.of() : blockRoutes);
        }
        static Delivery ok() { return ok(BigDecimal.ZERO, 0L, List.of(), false, false, false, 0L); }
        static Delivery ok(BigDecimal sold, long saleItems) {
            return ok(sold, saleItems, List.of(), saleItems > 0, false, false, 0L);
        }
        static Delivery ok(BigDecimal sold, long saleItems, List<UUID> droppedEntityIds, boolean autoSellUsed,
                           boolean autoPickupUsed, boolean autoBlockUsed, long fallbackDroppedItems) {
            return new Delivery(true, "", sold == null ? BigDecimal.ZERO : sold, Math.max(0L, saleItems),
                    droppedEntityIds, autoSellUsed, autoPickupUsed && !autoSellUsed, autoBlockUsed,
                    Math.max(0L, fallbackDroppedItems), Map.of());
        }
        static Delivery bulk(BigDecimal sold, long saleItems, List<UUID> droppedEntityIds,
                             Map<Integer, MutableBlockRoute> mutableRoutes) {
            Map<Integer, MiningRouteOutcome> routes = new HashMap<>();
            mutableRoutes.forEach((index, route) -> routes.put(index, route.freeze()));
            boolean autoSell = routes.values().stream().anyMatch(MiningRouteOutcome::autoSellBlock);
            boolean autoPickup = routes.values().stream().anyMatch(MiningRouteOutcome::autoPickupBlock);
            boolean autoBlock = routes.values().stream().anyMatch(MiningRouteOutcome::autoBlockApplied);
            long fallback = routes.values().stream().mapToLong(MiningRouteOutcome::droppedItems).sum();
            return new Delivery(true, "", sold == null ? BigDecimal.ZERO : sold, Math.max(0L, saleItems),
                    droppedEntityIds, autoSell, autoPickup, autoBlock, fallback, routes);
        }
        static Delivery fail(String error) {
            return new Delivery(false, error, BigDecimal.ZERO, 0L, List.of(), false, false, false, 0L, Map.of());
        }

        BulkMiningDeliveryResult actualResult() {
            return new BulkMiningDeliveryResult(sold, saleItems, droppedEntityIds, blockRoutes);
        }
    }
    public record ProcessResult(boolean handled, boolean cancel, int blocks, boolean suppressVanillaXp, String error) {
        static ProcessResult notHandled() { return new ProcessResult(false, false, 0, false, ""); }
        static ProcessResult trackedOnly(boolean suppressXp) { return new ProcessResult(false, false, 1, suppressXp, ""); }
        static ProcessResult handled(int blocks) { return new ProcessResult(true, false, blocks, true, ""); }
        static ProcessResult cancelled(String error) { return new ProcessResult(true, true, 0, false, error); }
    }
    public record NormalMiningPreparation(boolean prepared, boolean cancel, UUID playerId, UUID worldId,
                                          int x, int y, int z, String mineId, Material material,
                                          int vanillaXp, String source, boolean ownDrops,
                                          boolean suppressVanillaXp, String error) {
        static NormalMiningPreparation notHandled() {
            return new NormalMiningPreparation(false, false, null, null, 0, 0, 0, "",
                    Material.AIR, 0, "BUKKIT", false, false, "");
        }
        static NormalMiningPreparation cancelled(String error) {
            return new NormalMiningPreparation(false, true, null, null, 0, 0, 0, "",
                    Material.AIR, 0, "BUKKIT", false, false, error);
        }
        static NormalMiningPreparation prepared(UUID playerId, Block block, String mineId, Material material,
                                                int vanillaXp, String source, boolean ownDrops) {
            return new NormalMiningPreparation(true, false, playerId, block.getWorld().getUID(),
                    block.getX(), block.getY(), block.getZ(), mineId, material, vanillaXp,
                    source == null ? "BUKKIT" : source, ownDrops, true, "");
        }
        static NormalMiningPreparation trackedOnly(UUID playerId, Block block, String mineId, Material material,
                                                   int vanillaXp, String source, boolean suppressVanillaXp) {
            return new NormalMiningPreparation(true, false, playerId, block.getWorld().getUID(),
                    block.getX(), block.getY(), block.getZ(), mineId, material, vanillaXp,
                    source == null ? "BUKKIT" : source, false, suppressVanillaXp, "");
        }
    }
    public record BulkResult(boolean success, int blocks, String error) {
        static BulkResult failure(String error) { return new BulkResult(false, 0, error); }
    }
}
