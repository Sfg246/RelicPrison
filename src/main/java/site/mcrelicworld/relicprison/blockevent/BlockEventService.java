package site.mcrelicworld.relicprison.blockevent;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.blockevent.BlockEventCatalog.BlockEventDefinition;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.reward.FrozenRewardPlanCodec;
import site.mcrelicworld.relicprison.reward.RewardComponentRecord;
import site.mcrelicworld.relicprison.reward.RewardComponentState;
import site.mcrelicworld.relicprison.reward.RewardLedgerService.ComponentDraft;
import site.mcrelicworld.relicprison.reward.RewardPackageRecord;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class BlockEventService {
    private final RelicPrisonPlugin plugin;
    private final BlockEventRepository repository;
    private volatile BlockEventCatalog catalog = new BlockEventCatalog(List.of());
    private final Map<BlockEventRepository.StateKey, BlockEventRepository.State> states = new ConcurrentHashMap<>();

    public BlockEventService(RelicPrisonPlugin plugin, BlockEventRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public java.util.concurrent.CompletableFuture<Void> initializeAsync() {
        if (!plugin.config().snapshot().features().blockEvents()) return java.util.concurrent.CompletableFuture.completedFuture(null);
        try {
            catalog = BlockEventCatalog.load(plugin.getDataFolder());
        } catch (Exception ex) {
            return java.util.concurrent.CompletableFuture.failedFuture(ex);
        }
        return recoverIncompleteTriggers().thenCompose(recovered -> repository.loadAll()).thenAccept(loaded -> {
            states.clear();
            states.putAll(loaded);
        });
    }

    public void reload() throws Exception {
        apply(preview());
    }

    public BlockEventCatalog preview() throws Exception {
        return plugin.config().snapshot().features().blockEvents()
                ? BlockEventCatalog.load(plugin.getDataFolder()) : new BlockEventCatalog(List.of());
    }

    public void apply(BlockEventCatalog next) {
        catalog = next;
    }

    public BlockEventCatalog catalog() { return catalog; }

    public void handle(MiningAction action) {
        if (!plugin.config().snapshot().features().blockEvents()) return;
        if (catalog.events().isEmpty() || action.blocks() <= 0) return;
        Set<String> triggered = new HashSet<>();
        for (BlockEventDefinition event : catalog.events()) {
            if (!matches(event, action)) continue;
            String identity = event.id() + ':' + action.actionId();
            if (!triggered.add(identity)) continue;
            TriggerDecision decision = triggerDecision(event, action);
            if (!decision.qualifies()) continue;
            BlockEventRepository.TriggerRecord trigger = triggerRecord(event, action);
            String packageId = packageId(trigger);
            List<ComponentDraft> drafts = rewardDrafts(event, action.playerId(), action.player().getName(),
                    action.blocks());
            String frozenPayload = FrozenRewardPlanCodec.encode(drafts);
            long now = System.currentTimeMillis();
            repository.reserveFrozenTrigger(trigger, decision.stateUpdates(), frozenPayload).thenCompose(created -> {
                if (created) {
                    states.putAll(decision.stateUpdates());
                    BlockEventRepository.IncompleteTrigger incomplete = new BlockEventRepository.IncompleteTrigger(
                            trigger.triggerId(), trigger.eventId(), trigger.playerId(), trigger.operationId(),
                            trigger.blockIdentity(), trigger.triggerType(), trigger.periodId(), trigger.milestone(),
                            packageId, "PROGRESS_RECORDED", frozenPayload, "", trigger.logicalClaimKey(), 0, now);
                    RewardPackageRecord rewardPackage = new RewardPackageRecord(packageId, "block-event",
                            trigger.triggerId(), action.playerId(), RewardComponentState.PENDING, now, now,
                            frozenPayload, "");
                    List<RewardComponentRecord> components = drafts.stream()
                            .map(draft -> draft.toRecord(packageId, "block-event", trigger.triggerId(),
                                    action.playerId(), now))
                            .toList();
                    return repository.createPackageForExistingTrigger(incomplete, rewardPackage, components,
                            frozenPayload);
                }
                return repository.packageExists(packageId);
            }).whenComplete((processed, error) -> {
                if (error != null) {
                    plugin.getLogger().warning("Block event trigger processing failed for " + event.id()
                            + ": " + rootMessage(error));
                    return;
                }
                if (Boolean.TRUE.equals(processed)) {
                    plugin.rewardLedger().deliverDueAsync();
                    if (plugin.gangs() != null) plugin.gangs().recordContribution(action.playerId(),
                            site.mcrelicworld.relicprison.gang.GangConfig.ContributionType.BLOCK_EVENTS,
                            java.math.BigDecimal.ONE, event.id(), "block-event:" + trigger.triggerId())
                            .exceptionally(failure -> null);
                }
            });
        }
    }

    private BlockEventRepository.TriggerRecord triggerRecord(BlockEventDefinition event, MiningAction action) {
        String triggerType = event.trigger().name().toLowerCase(Locale.ROOT);
        String periodId = event.trigger() == BlockEventCatalog.Trigger.DAILY_TARGET
                ? action.profile().dailyPeriod() : "";
        long milestone = event.trigger() == BlockEventCatalog.Trigger.EVERY_X_BLOCKS && event.everyXBlocks() > 0
                ? Math.max(1L, action.profile().lifetimeBlocks() / event.everyXBlocks()) : 0L;
        String blockIdentity = action.bulk() ? "bulk-action" : "normal-action";
        String logicalClaimKey = logicalClaimKey(event, action, triggerType, periodId, milestone, blockIdentity);
        String triggerId = "be-" + UUID.nameUUIDFromBytes(logicalClaimKey.getBytes(StandardCharsets.UTF_8));
        return new BlockEventRepository.TriggerRecord(triggerId, event.id(), action.playerId(),
                action.actionId().toString(), blockIdentity, triggerType, periodId, milestone, logicalClaimKey);
    }

    private String logicalClaimKey(BlockEventDefinition event, MiningAction action, String triggerType,
                                   String periodId, long milestone, String blockIdentity) {
        String entitlement = switch (event.trigger()) {
            case FIRST_TIME -> "first";
            case DAILY_TARGET -> "daily:" + periodId;
            case EVERY_X_BLOCKS -> "milestone:" + milestone;
            case CHANCE_PER_BLOCK, CHANCE_PER_ACTION -> "occurrence:" + action.actionId() + ':' + blockIdentity;
        };
        String raw = event.id() + '|' + action.playerId() + '|' + triggerType + '|' + entitlement;
        return "be-claim-" + UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }

    private boolean matches(BlockEventDefinition event, MiningAction action) {
        if (event.miningType() == BlockEventCatalog.MiningType.NORMAL && action.bulk()) return false;
        if (event.miningType() == BlockEventCatalog.MiningType.BULK && !action.bulk()) return false;
        if (!event.mines().isEmpty() && action.mineIds().stream().noneMatch(event.mines()::contains)) return false;
        if (!event.materials().isEmpty() && action.materials().keySet().stream().noneMatch(event.materials()::contains)) return false;
        if (!event.customBlocks().isEmpty() && action.customBlocks().keySet().stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).noneMatch(event.customBlocks()::contains)) return false;
        PlayerProfile profile = action.profile();
        if (event.minimumRank() != null && plugin.rankService().indexOf(profile.currentRank())
                < plugin.rankService().indexOf(event.minimumRank())) return false;
        if (event.minimumPrestige() != null && plugin.prestigeService().indexOf(profile.currentPrestige())
                < plugin.prestigeService().indexOf(event.minimumPrestige())) return false;
        for (String permission : event.permissions()) if (!action.player().hasPermission(permission)) return false;
        return true;
    }

    private TriggerDecision triggerDecision(BlockEventDefinition event, MiningAction action) {
        long now = System.currentTimeMillis();
        Map<BlockEventRepository.StateKey, BlockEventRepository.State> updates = new java.util.HashMap<>();
        BlockEventRepository.StateKey cooldownKey = new BlockEventRepository.StateKey(action.playerId(), event.id(), "cooldown");
        BlockEventRepository.State cooldown = states.get(cooldownKey);
        if (event.cooldownMillis() > 0 && cooldown != null && now - cooldown.lastTriggeredAt() < event.cooldownMillis()) {
            return TriggerDecision.no();
        }
        boolean qualifies = switch (event.trigger()) {
            case CHANCE_PER_BLOCK -> chanceAnyBlock(event.chance(), action.blocks());
            case CHANCE_PER_ACTION -> ThreadLocalRandom.current().nextDouble() <= event.chance();
            case FIRST_TIME -> firstTime(event, action, updates);
            case EVERY_X_BLOCKS -> everyX(event, action);
            case DAILY_TARGET -> dailyTarget(event, action, updates);
        };
        if (!qualifies) return TriggerDecision.no();
        updates.put(cooldownKey, new BlockEventRepository.State(now, 0L, 0L));
        return new TriggerDecision(true, Map.copyOf(updates));
    }

    private boolean firstTime(BlockEventDefinition event, MiningAction action,
                              Map<BlockEventRepository.StateKey, BlockEventRepository.State> updates) {
        String key = "first";
        BlockEventRepository.StateKey stateKey = new BlockEventRepository.StateKey(action.playerId(), event.id(), key);
        if (states.getOrDefault(stateKey, new BlockEventRepository.State(0, 0, 0)).completedAt() > 0) return false;
        BlockEventRepository.State state = new BlockEventRepository.State(System.currentTimeMillis(), System.currentTimeMillis(), 1);
        updates.put(stateKey, state);
        return true;
    }

    private boolean everyX(BlockEventDefinition event, MiningAction action) {
        if (event.everyXBlocks() <= 0) return false;
        long after = action.profile().lifetimeBlocks();
        long before = Math.max(0L, after - action.blocks());
        return after / event.everyXBlocks() > before / event.everyXBlocks();
    }

    private boolean dailyTarget(BlockEventDefinition event, MiningAction action,
                                Map<BlockEventRepository.StateKey, BlockEventRepository.State> updates) {
        if (event.dailyTarget() <= 0) return false;
        String key = "daily:" + action.profile().dailyPeriod();
        BlockEventRepository.StateKey stateKey = new BlockEventRepository.StateKey(action.playerId(), event.id(), key);
        if (states.getOrDefault(stateKey, new BlockEventRepository.State(0, 0, 0)).completedAt() > 0) return false;
        if (action.profile().dailyBlocks() < event.dailyTarget()) return false;
        BlockEventRepository.State state = new BlockEventRepository.State(System.currentTimeMillis(), System.currentTimeMillis(), 1);
        updates.put(stateKey, state);
        return true;
    }

    private static boolean chanceAnyBlock(double chance, int blocks) {
        return ThreadLocalRandom.current().nextDouble() <= chanceAnyBlockProbability(chance, blocks);
    }

    static double chanceAnyBlockProbability(double chance, int blocks) {
        if (blocks <= 0 || chance <= 0.0D) return 0.0D;
        if (chance >= 1.0D) return 1.0D;
        return 1.0D - Math.pow(1.0D - chance, blocks);
    }

    private List<ComponentDraft> rewardDrafts(BlockEventDefinition event, UUID playerId, String playerName,
                                              int blocks) {
        List<ComponentDraft> drafts = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (event.rewards().money().signum() > 0) {
            drafts.add(ComponentDraft.money(event.id() + "-money", event.rewards().money(), now));
        }
        if (event.rewards().experience() > 0) {
            drafts.add(ComponentDraft.experience(event.id() + "-experience", event.rewards().experience(), now));
        }
        int itemCount = 0;
        for (BlockEventCatalog.ItemReward reward : event.rewards().items().stream().limit(event.rewardLimit()).toList()) {
            if (itemCount++ >= event.rewardLimit()) break;
            if (reward.itemId().contains(":")) {
                drafts.add(ComponentDraft.itemsAdderItem(event.id() + "-item-" + itemCount,
                        reward.itemId(), reward.amount(), now));
            } else {
                Material material = Material.matchMaterial(reward.itemId().toUpperCase(Locale.ROOT));
                if (material != null && material.isItem()) {
                    drafts.add(ComponentDraft.vanillaItem(event.id() + "-item-" + itemCount,
                            material, reward.amount(), now));
                }
            }
        }
        if (event.rewards().booster() != null) {
            BlockEventCatalog.BoosterReward booster = event.rewards().booster();
            drafts.add(ComponentDraft.booster(event.id() + "-booster", booster.serverWide(),
                    booster.multiplier(), booster.durationMillis(), playerId.toString(), now));
        }
        int commandCount = 0;
        for (String configured : event.rewards().commands()) {
            if (commandCount++ >= event.commandLimit()) break;
            String command = configured.replace("%player%", playerName)
                    .replace("%uuid%", playerId.toString())
                    .replace("%event%", event.displayName())
                    .replace("%blocks%", String.valueOf(blocks));
            drafts.add(ComponentDraft.consoleCommand(event.id() + "-command-" + commandCount, command, now));
        }
        int announcementCount = 0;
        for (String announcement : event.rewards().announcements()) {
            drafts.add(ComponentDraft.announcement(event.id() + "-announcement-" + (++announcementCount),
                    announcement.replace("%player%", playerName).replace("%blocks%",
                            String.valueOf(blocks)).replace("%event%", event.displayName()), now));
        }
        return List.copyOf(drafts);
    }

    public java.util.concurrent.CompletableFuture<Integer> recoverIncompleteTriggers() {
        return repository.incompleteTriggers(200).thenCompose(triggers -> {
            List<java.util.concurrent.CompletableFuture<?>> recoveries = new ArrayList<>();
            for (BlockEventRepository.IncompleteTrigger trigger : triggers) {
                recoveries.add(recoverIncompleteTrigger(trigger).exceptionally(error -> {
                    plugin.structuredLogger().warning(site.mcrelicworld.relicprison.logging.LogCategory.MINING,
                            "block-event-recovery-failed trigger=" + trigger.triggerId()
                                    + " event=" + trigger.eventId() + " player=" + trigger.playerId()
                                    + " failure=" + rootMessage(error));
                    return null;
                }));
            }
            return java.util.concurrent.CompletableFuture.allOf(recoveries.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .thenApply(ignored -> triggers.size());
        });
    }

    private java.util.concurrent.CompletableFuture<Void> recoverIncompleteTrigger(
            BlockEventRepository.IncompleteTrigger trigger) {
        String existingPackage = trigger.packageId();
        if (existingPackage != null && !existingPackage.isBlank()) {
            return repository.packageExists(existingPackage).thenCompose(exists -> {
                if (exists) return repository.markTriggerComplete(trigger.triggerId(), existingPackage);
                return createRecoveredPackage(trigger);
            });
        }
        return createRecoveredPackage(trigger);
    }

    private java.util.concurrent.CompletableFuture<Void> createRecoveredPackage(
            BlockEventRepository.IncompleteTrigger trigger) {
        long now = System.currentTimeMillis();
        String packageId = trigger.packageId() == null || trigger.packageId().isBlank()
                ? packageId(trigger) : trigger.packageId();
        String frozenPayload = trigger.frozenPayload();
        final List<ComponentDraft> drafts;
        try {
            drafts = FrozenRewardPlanCodec.decode(frozenPayload);
        } catch (IllegalArgumentException error) {
            return repository.markRecoverable(trigger.triggerId(),
                    "frozen reward payload is unavailable or invalid: " + rootMessage(error));
        }
        RewardPackageRecord rewardPackage = new RewardPackageRecord(packageId, "block-event",
                trigger.triggerId(), trigger.playerId(), RewardComponentState.PENDING, now, now, frozenPayload, "");
        List<RewardComponentRecord> components = drafts.stream()
                .map(draft -> draft.toRecord(packageId, "block-event", trigger.triggerId(), trigger.playerId(), now))
                .toList();
        return repository.createPackageForExistingTrigger(trigger, rewardPackage, components, frozenPayload)
                .thenCompose(created -> created ? plugin.rewardLedger().deliverDueAsync()
                        : java.util.concurrent.CompletableFuture.completedFuture(null));
    }

    private static String packageId(BlockEventRepository.TriggerRecord trigger) {
        return trigger.triggerId() + "-pkg";
    }

    private static String packageId(BlockEventRepository.IncompleteTrigger trigger) {
        return trigger.triggerId() + "-pkg";
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record MiningAction(UUID actionId, Player player, PlayerProfile profile, Set<String> mineIds,
                               Map<Material, Integer> materials, Map<String, Integer> customBlocks,
                               int blocks, boolean bulk) {
        public MiningAction(UUID actionId, Player player, PlayerProfile profile, Set<String> mineIds,
                            Map<Material, Integer> materials, int blocks, boolean bulk) {
            this(actionId, player, profile, mineIds, materials, Map.of(), blocks, bulk);
        }
        public MiningAction {
            customBlocks = Map.copyOf(customBlocks == null ? Map.of() : customBlocks);
        }
        public UUID playerId() { return player.getUniqueId(); }
    }

    private record TriggerDecision(boolean qualifies, Map<BlockEventRepository.StateKey,
            BlockEventRepository.State> stateUpdates) {
        static TriggerDecision no() { return new TriggerDecision(false, Map.of()); }
    }
}
