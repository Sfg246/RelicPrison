package site.mcrelicworld.relicprison.leaderboard;

import org.bukkit.Material;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.model.LeaderboardEntry;
import site.mcrelicworld.relicprison.gang.GangConfig;
import site.mcrelicworld.relicprison.reward.RewardComponentRecord;
import site.mcrelicworld.relicprison.reward.RewardComponentState;
import site.mcrelicworld.relicprison.reward.RewardLedgerService.ComponentDraft;
import site.mcrelicworld.relicprison.reward.RewardPackageRecord;
import site.mcrelicworld.relicprison.util.ColorUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class LeaderboardRewardService {
    private final RelicPrisonPlugin plugin;
    private final LeaderboardRewardRepository repository;
    private volatile LeaderboardRewardConfig config = new LeaderboardRewardConfig(false, true, "", Map.of());

    public LeaderboardRewardService(RelicPrisonPlugin plugin, LeaderboardRewardRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public CompletableFuture<Void> initializeAsync() {
        try {
            config = LeaderboardRewardConfig.load(plugin.getDataFolder());
            return repository.recoverIncompletePeriods(50).thenAccept(recovered -> {
                if (recovered > 0) {
                    plugin.structuredLogger().info(site.mcrelicworld.relicprison.logging.LogCategory.DIAGNOSTIC,
                            "leaderboard-recovery-processed periods=" + recovered);
                }
            });
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    public LeaderboardRewardConfig preview() throws Exception { return LeaderboardRewardConfig.load(plugin.getDataFolder()); }
    public void reload() throws Exception { apply(preview()); }
    public void apply(LeaderboardRewardConfig next) { config = next; }
    public LeaderboardRewardConfig config() { return config; }

    public CompletableFuture<Void> recoverAsync() {
        return repository.recoverIncompletePeriods(50).thenCompose(recovered -> {
            if (recovered > 0) {
                plugin.structuredLogger().info(site.mcrelicworld.relicprison.logging.LogCategory.DIAGNOSTIC,
                        "leaderboard-recovery-processed periods=" + recovered);
            }
            return plugin.rewardLedger().deliverDueAsync();
        });
    }

    public CompletableFuture<RewardPreview> preview(String boardId, String requestedPeriodId) {
        return winners(boardId, requestedPeriodId).thenApply(result ->
                new RewardPreview(result.board().id(), result.periodId(), true, false, result.entries(),
                        "dry-run"));
    }

    public CompletableFuture<RewardPreview> finalizePeriod(String boardId, String requestedPeriodId, UUID actor) {
        if (!config.enabled()) return CompletableFuture.failedFuture(new IllegalStateException("Leaderboard rewards are disabled"));
        return winners(boardId, requestedPeriodId).thenCompose(result -> {
            return finalizeTransaction(result, actor).thenCompose(outcome -> {
                if (!outcome.created() && !outcome.finalized()) {
                    return CompletableFuture.completedFuture(new RewardPreview(result.board().id(),
                            result.periodId(), false, outcome.finalized(), result.entries(), outcome.state()));
                }
                return recordGangContributions(result).thenCompose(ignored ->
                        plugin.rewardLedger().deliverDueAsync()).thenApply(ignored ->
                        new RewardPreview(result.board().id(), result.periodId(), false, false,
                                result.entries(), outcome.state()));
            });
        });
    }

    private CompletableFuture<Void> recordGangContributions(Winners winners) {
        if (plugin.gangs() == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<?>[] records = winners.entries().stream()
                .filter(entry -> winners.board().rewards().stream()
                        .anyMatch(reward -> reward.positions().contains(entry.position())))
                .map(entry -> plugin.gangs().recordContribution(entry.playerId(),
                        GangConfig.ContributionType.LEADERBOARD, BigDecimal.ONE, winners.board().id(),
                        "leaderboard:" + winners.board().id() + ':' + winners.periodId() + ':' + entry.playerId()))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(records);
    }

    public CompletableFuture<List<LeaderboardRewardRecord>> history(int limit) {
        return repository.history(limit);
    }

    public CompletableFuture<List<LeaderboardRewardRecord>> pending(int limit) {
        return repository.failedOrPending(limit);
    }

    public CompletableFuture<Void> retry(String boardId, String periodId, UUID playerId, String rewardId, UUID actor) {
        return repository.record(LeaderboardBoard.normalizeId(boardId), periodId, playerId,
                LeaderboardBoard.normalizeId(rewardId)).thenCompose(record -> {
            if (record.isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown reward ledger entry"));
            return plugin.rewardLedger().deliverPending(playerId).thenCompose(ignored ->
                    repository.mark(record.get().boardId(), record.get().periodId(), playerId,
                            record.get().rewardDefinitionId(),
                            RewardDeliveryState.PACKAGE_CREATED, record.get().attemptCount(),
                            "retry delegated to shared reward ledger"));
        });
    }

    public CompletableFuture<Void> deliverPending(UUID playerId) {
        return repository.pendingFor(playerId, 50).thenCompose(records -> {
            CompletableFuture<?>[] futures = records.stream()
                    .map(record -> retry(record.boardId(), record.periodId(), record.playerId(),
                            record.rewardDefinitionId(), null).exceptionally(error -> null))
                    .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(futures);
        });
    }

    private CompletableFuture<Winners> winners(String boardId, String requestedPeriodId) {
        LeaderboardRewardConfig.BoardRewards board = config.boards().get(LeaderboardBoard.normalizeId(boardId));
        if (board == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown reward board"));
        if (!board.period().equals("lifetime") && !board.metric().equals("blocks")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Period rewards currently require the blocks metric"));
        }
        int maxPosition = board.rewards().stream().flatMap(reward -> reward.positions().stream())
                .mapToInt(Integer::intValue).max().orElse(1);
        String periodId = requestedPeriodId == null || requestedPeriodId.isBlank()
                ? LeaderboardPeriods.completedPeriodId(board.period(), System.currentTimeMillis(),
                plugin.config().snapshot().timezone(), plugin.config().snapshot().leaderboards())
                : requestedPeriodId;
        String queryPeriod = board.period().equals("lifetime") ? "lifetime" : board.period() + ":" + periodId;
        return plugin.statistics().leaderboard(board.metric(), queryPeriod, maxPosition)
                .thenApply(entries -> new Winners(board, periodId, entries));
    }

    private CompletableFuture<LeaderboardRewardRepository.FinalizationResult> finalizeTransaction(Winners winners,
                                                                                                  UUID actor) {
        LeaderboardRewardConfig.BoardRewards board = winners.board();
        String periodId = winners.periodId();
        List<LeaderboardEntry> entries = winners.entries();
        AtomicInteger commandBudget = new AtomicInteger(plugin.config().snapshot().leaderboards().rewardCommandLimit());
        long now = System.currentTimeMillis();
        String snapshot = snapshot(entries);
        List<LeaderboardRewardRepository.WinnerSnapshot> snapshots = new ArrayList<>();
        List<LeaderboardRewardRepository.RewardBundle> rewards = new ArrayList<>();
        List<FrozenLeaderboardPlan.Entry> frozenPlan = new ArrayList<>();
        for (LeaderboardEntry entry : entries) {
            snapshots.add(new LeaderboardRewardRepository.WinnerSnapshot(board.id(), periodId, entry.playerId(),
                    entry.playerName(), entry.position(), entry.value(), entry.playerName().toLowerCase(Locale.ROOT)
                    + ':' + entry.playerId(), frozenRewards(board, entry), now));
            for (LeaderboardRewardConfig.RewardDefinition reward : board.rewards()) {
                if (!reward.positions().contains(entry.position())) continue;
                String packageId = packageId(board.id(), periodId, entry.playerId(), reward.id());
                String frozenPayload = frozenPayload(board, periodId, entry, reward);
                List<ComponentDraft> drafts = rewardDrafts(reward, entry, actor);
                boolean intentionallyEmpty = intentionallyEmpty(reward);
                boolean commandsReserved = reserveCommands(reward.commands().size(), commandBudget);
                String validationFailure = rewardValidationFailure(reward, commandsReserved);
                frozenPlan.add(new FrozenLeaderboardPlan.Entry(entry.playerId(), entry.playerName(),
                        entry.position(), entry.value(), reward.id(), packageId, frozenPayload,
                        intentionallyEmpty, validationFailure, drafts));
                if (!validationFailure.isBlank()) {
                    LeaderboardRewardRecord ledger = new LeaderboardRewardRecord(board.id(), periodId,
                            entry.playerId(), entry.position(), reward.id(), RewardDeliveryState.STAFF_REVIEW,
                            0, now, now, validationFailure);
                    rewards.add(new LeaderboardRewardRepository.RewardBundle(ledger,
                            new RewardPackageRecord(packageId, "leaderboard", board.id() + ':' + periodId,
                                    entry.playerId(), RewardComponentState.STAFF_REVIEW, now, now, frozenPayload,
                                    validationFailure),
                            List.of()));
                    continue;
                }
                RewardPackageRecord rewardPackage = new RewardPackageRecord(packageId, "leaderboard",
                        board.id() + ':' + periodId, entry.playerId(), RewardComponentState.PENDING, now, now,
                        frozenPayload, "");
                List<RewardComponentRecord> components = drafts.stream()
                        .map(draft -> draft.toRecord(packageId, "leaderboard", board.id() + ':' + periodId,
                                entry.playerId(), now))
                        .toList();
                LeaderboardRewardRecord ledger = new LeaderboardRewardRecord(board.id(), periodId, entry.playerId(),
                        entry.position(), reward.id(), RewardDeliveryState.PACKAGE_CREATED, 1, now, now,
                        "reward package created atomically with finalization");
                rewards.add(new LeaderboardRewardRepository.RewardBundle(ledger, rewardPackage, components));
            }
        }
        LeaderboardRewardRepository.FinalizationPeriod period =
                new LeaderboardRewardRepository.FinalizationPeriod(board.id(), periodId, board.metric(),
                        board.period(), snapshot, FrozenLeaderboardPlan.encode(frozenPlan), now);
        return repository.finalizePeriod(period, snapshots, rewards);
    }

    private boolean intentionallyEmpty(LeaderboardRewardConfig.RewardDefinition reward) {
        return reward.money().signum() == 0 && reward.experience() == 0 && reward.items().isEmpty()
                && reward.boosters().isEmpty()
                && reward.commands().isEmpty() && (!config.announceRewards() || reward.announcements().isEmpty());
    }

    private String rewardValidationFailure(LeaderboardRewardConfig.RewardDefinition reward,
                                           boolean commandsReserved) {
        if (!commandsReserved) return "configured command limit reached before package creation";
        for (LeaderboardRewardConfig.ItemReward item : reward.items()) {
            if (item.itemId().contains(":")) {
                if (plugin.itemsAdder() == null || !plugin.itemsAdder().enabled()) {
                    return "ItemsAdder item cannot be frozen while the integration is unavailable: "
                            + item.itemId();
                }
            } else {
                Material material = Material.matchMaterial(item.itemId().toUpperCase(Locale.ROOT));
                if (material == null || !material.isItem()) return "invalid reward item: " + item.itemId();
            }
        }
        return "";
    }

    private boolean reserveCommands(int count, AtomicInteger budget) {
        if (count <= 0) return true;
        while (true) {
            int current = budget.get();
            if (current < count) return false;
            if (budget.compareAndSet(current, current - count)) return true;
        }
    }

    private List<ComponentDraft> rewardDrafts(LeaderboardRewardConfig.RewardDefinition reward,
                                              LeaderboardEntry entry, UUID actor) {
        List<ComponentDraft> drafts = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (reward.money().signum() > 0) drafts.add(ComponentDraft.money(reward.id() + "-money",
                reward.money(), now));
        if (reward.experience() > 0) drafts.add(ComponentDraft.experience(reward.id() + "-experience",
                reward.experience(), now));
        int itemCount = 0;
        for (LeaderboardRewardConfig.ItemReward item : reward.items()) {
            if (item.itemId().contains(":")) {
                drafts.add(ComponentDraft.itemsAdderItem("item-" + (++itemCount), item.itemId(),
                        item.amount(), now));
            } else {
                Material material = Material.matchMaterial(item.itemId().toUpperCase(Locale.ROOT));
                if (material != null && material.isItem()) {
                    drafts.add(ComponentDraft.vanillaItem("item-" + (++itemCount), material, item.amount(), now));
                }
            }
        }
        int boosterCount = 0;
        for (LeaderboardRewardConfig.BoosterReward booster : reward.boosters()) {
            drafts.add(ComponentDraft.booster("booster-" + (++boosterCount), booster.serverWide(),
                    booster.multiplier(), booster.durationMillis(), actor == null ? "leaderboard" : actor.toString(),
                    now));
        }
        int commandCount = 0;
        for (String command : reward.commands()) {
            drafts.add(ComponentDraft.consoleCommand("command-" + (++commandCount), placeholders(command, entry), now));
        }
        if (config.announceRewards()) {
            int announcementCount = 0;
            for (String announcement : reward.announcements()) {
                drafts.add(ComponentDraft.announcement("announcement-" + (++announcementCount),
                        ColorUtil.color(placeholders(announcement, entry)), now));
            }
        }
        return List.copyOf(drafts);
    }

    private static String placeholders(String value, LeaderboardEntry entry) {
        return value.replace("%player%", entry.playerName())
                .replace("%uuid%", entry.playerId().toString())
                .replace("%position%", String.valueOf(entry.position()))
                .replace("%value%", entry.value().stripTrailingZeros().toPlainString());
    }

    private static String snapshot(List<LeaderboardEntry> entries) {
        StringBuilder builder = new StringBuilder();
        for (LeaderboardEntry entry : entries) {
            builder.append(entry.position()).append(',')
                    .append(entry.playerId()).append(',')
                    .append(entry.playerName().replace(",", "_")).append(',')
                    .append(entry.value().stripTrailingZeros().toPlainString()).append('\n');
        }
        return builder.toString();
    }

    private static String frozenRewards(LeaderboardRewardConfig.BoardRewards board, LeaderboardEntry entry) {
        return board.rewards().stream()
                .filter(reward -> reward.positions().contains(entry.position()))
                .map(LeaderboardRewardConfig.RewardDefinition::id)
                .sorted()
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "," + right);
    }

    private static String packageId(String boardId, String periodId, UUID playerId, String rewardId) {
        String raw = "lb-" + boardId + '-' + periodId + '-' + playerId + '-' + rewardId;
        return raw.replaceAll("[^A-Za-z0-9_.:-]", "_").toLowerCase(Locale.ROOT);
    }

    private static String frozenPayload(LeaderboardRewardConfig.BoardRewards board, String periodId,
                                        LeaderboardEntry entry,
                                        LeaderboardRewardConfig.RewardDefinition reward) {
        return "board=" + board.id() + ";period=" + periodId + ";metric=" + board.metric()
                + ";player=" + entry.playerId() + ";name=" + entry.playerName().replace(';', '_')
                + ";position=" + entry.position() + ";value=" + entry.value().stripTrailingZeros().toPlainString()
                + ";reward=" + reward.id();
    }

    private record Winners(LeaderboardRewardConfig.BoardRewards board, String periodId,
                           List<LeaderboardEntry> entries) { }

    public record RewardPreview(String boardId, String periodId, boolean dryRun, boolean alreadyFinalized,
                                List<LeaderboardEntry> winners, String state) {
        public RewardPreview { winners = List.copyOf(winners); }
    }
}
