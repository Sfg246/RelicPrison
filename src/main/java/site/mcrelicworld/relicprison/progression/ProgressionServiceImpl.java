package site.mcrelicworld.relicprison.progression;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.ProgressionService;
import site.mcrelicworld.relicprison.api.event.RelicPlayerProgressionRepairEvent;
import site.mcrelicworld.relicprison.api.event.RelicPlayerPrestigeEvent;
import site.mcrelicworld.relicprison.api.event.RelicPlayerRankUpEvent;
import site.mcrelicworld.relicprison.api.model.ProgressionResult;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.database.PlayerProfileRepository;
import site.mcrelicworld.relicprison.economy.VaultEconomyAdapter;
import site.mcrelicworld.relicprison.integration.LuckPermsIntegration;
import site.mcrelicworld.relicprison.reward.RewardLedgerService;
import site.mcrelicworld.relicprison.util.ColorUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressionServiceImpl implements ProgressionService {
    private final RelicPrisonPlugin plugin;
    private final PlayerProfileRepository profiles;
    private final RankServiceImpl ranks;
    private final PrestigeServiceImpl prestiges;
    private final VaultEconomyAdapter economy;
    private final LuckPermsIntegration luckPerms;
    private final ProgressionTransactionRepository transactions;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public ProgressionServiceImpl(RelicPrisonPlugin plugin, PlayerProfileRepository profiles,
                                  RankServiceImpl ranks, PrestigeServiceImpl prestiges,
                                  VaultEconomyAdapter economy, LuckPermsIntegration luckPerms,
                                  ProgressionTransactionRepository transactions) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.prestiges = prestiges;
        this.economy = economy;
        this.luckPerms = luckPerms;
        this.transactions = transactions;
    }

    @Override public Optional<String> currentRank(UUID playerId) {
        return profiles.cachedProfile(playerId).map(PlayerProfile::currentRank);
    }

    @Override public Optional<String> currentPrestige(UUID playerId) {
        return profiles.cachedProfile(playerId).map(PlayerProfile::currentPrestige);
    }

    @Override public CompletableFuture<ProgressionResult> rankUp(UUID playerId, boolean maximum) {
        requireMainThread();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return CompletableFuture.completedFuture(ProgressionResult.failure("player-offline"));
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        if (profile == null) return CompletableFuture.completedFuture(ProgressionResult.failure("profile-loading"));
        if (!inFlight.add(playerId)) return CompletableFuture.completedFuture(ProgressionResult.failure("already-processing"));
        try {
            RankDefinition current = ranks.definition(profile.currentRank()).orElse(null);
            if (current == null) return finishImmediate(playerId, ProgressionResult.failure("invalid-rank"));
            RankDefinition next = ranks.next(current.id()).orElse(null);
            if (next == null) return finishImmediate(playerId, ProgressionResult.failure("max-rank"));
            if (!meetsRequirements(player, profile, next.permission(), next.requirements())) {
                return finishImmediate(playerId, ProgressionResult.failure("requirements-not-met"));
            }
            BigDecimal multiplier = prestiges.rankCostMultiplier(profile.currentPrestige());
            BigDecimal balance = economy.balance(player);
            RankDefinition target = next;
            BigDecimal total = rankCost(current, next).multiply(multiplier);
            if (maximum) {
                BigDecimal running = BigDecimal.ZERO;
                RankDefinition cursor = current;
                while (true) {
                    RankDefinition candidate = ranks.next(cursor.id()).orElse(null);
                    if (candidate == null || !meetsRequirements(player, profile, candidate.permission(), candidate.requirements())) break;
                    BigDecimal cost = rankCost(cursor, candidate).multiply(multiplier);
                    if (running.add(cost).compareTo(balance) > 0) break;
                    running = running.add(cost);
                    target = candidate;
                    cursor = candidate;
                }
                total = running;
            }
            if (total.signum() <= 0 || balance.compareTo(total) < 0 || target.id().equals(current.id())) {
                return finishImmediate(playerId, ProgressionResult.failure("not-enough-money"));
            }
            RelicPlayerRankUpEvent event = new RelicPlayerRankUpEvent(playerId, current.id(), target.id(), total);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return finishImmediate(playerId, ProgressionResult.failure("cancelled"));
            RankDefinition finalTarget = target;
            String oldRank = current.id();
            String newRank = finalTarget.id();
            BigDecimal charged = total;
            int oldIndex = ranks.indexOf(oldRank);
            int targetIndex = ranks.indexOf(newRank);
            List<RankDefinition> crossedRanks = ranks.definitions().subList(oldIndex + 1, targetIndex + 1).stream()
                    .filter(RankDefinition::enabled).toList();
            CompletableFuture<ProgressionResult> result = new CompletableFuture<>();
            ProgressionTransactionRecord transaction = newTransaction(playerId, ProgressionOperationType.RANKUP,
                    oldRank, profile.currentPrestige(), newRank, profile.currentPrestige(), charged,
                    "maximum=" + maximum);
            transactions.createIfNoActive(transaction).whenComplete((created, createError) ->
                    sync(() -> {
                        if (createError != null) {
                            inFlight.remove(playerId);
                            result.complete(ProgressionResult.failure("database-failed"));
                            return;
                        }
                        if (!created.created()) {
                            inFlight.remove(playerId);
                            result.complete(ProgressionResult.failure("already-processing"));
                            return;
                        }
                        continueRankup(created.record(), current, finalTarget, crossedRanks, result);
                    }));
            return result;
        } catch (RuntimeException ex) {
            inFlight.remove(playerId);
            CompletableFuture<ProgressionResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    @Override public CompletableFuture<ProgressionResult> prestige(UUID playerId) {
        requireMainThread();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return CompletableFuture.completedFuture(ProgressionResult.failure("player-offline"));
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        if (profile == null) return CompletableFuture.completedFuture(ProgressionResult.failure("profile-loading"));
        if (!inFlight.add(playerId)) return CompletableFuture.completedFuture(ProgressionResult.failure("already-processing"));
        try {
            RankDefinition currentRank = ranks.definition(profile.currentRank()).orElse(null);
            if (currentRank == null || !currentRank.id().equals(ranks.last().id())) {
                return finishImmediate(playerId, ProgressionResult.failure("requires-max-rank"));
            }
            PrestigeDefinition next = prestiges.next(profile.currentPrestige()).orElse(null);
            if (next == null) return finishImmediate(playerId, ProgressionResult.failure("max-prestige"));
            if (!meetsRequirements(player, profile, next.permission(), next.requirements())) {
                return finishImmediate(playerId, ProgressionResult.failure("requirements-not-met"));
            }
            if (economy.balance(player).compareTo(next.cost()) < 0) {
                return finishImmediate(playerId, ProgressionResult.failure("not-enough-money"));
            }
            RelicPlayerPrestigeEvent event = new RelicPlayerPrestigeEvent(playerId, profile.currentPrestige(), next.id(), next.cost());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return finishImmediate(playerId, ProgressionResult.failure("cancelled"));
            String oldPrestige = profile.currentPrestige();
            String oldRank = profile.currentRank();
            RankDefinition startingRank = ranks.definition(plugin.config().snapshot().progression().startingRank()).orElse(ranks.first());
            CompletableFuture<ProgressionResult> result = new CompletableFuture<>();
            ProgressionTransactionRecord transaction = newTransaction(playerId, ProgressionOperationType.PRESTIGE,
                    oldRank, oldPrestige, startingRank.id(), next.id(), next.cost(), "");
            transactions.createIfNoActive(transaction).whenComplete((created, createError) ->
                    sync(() -> {
                        if (createError != null) {
                            inFlight.remove(playerId);
                            result.complete(ProgressionResult.failure("database-failed"));
                            return;
                        }
                        if (!created.created()) {
                            inFlight.remove(playerId);
                            result.complete(ProgressionResult.failure("already-processing"));
                            return;
                        }
                        continuePrestige(created.record(), currentRank, startingRank, next, result);
                    }));
            return result;
        } catch (RuntimeException ex) {
            inFlight.remove(playerId);
            CompletableFuture<ProgressionResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    private ProgressionTransactionRecord newTransaction(UUID playerId, ProgressionOperationType type,
                                                        String previousRank, String previousPrestige,
                                                        String targetRank, String targetPrestige,
                                                        BigDecimal expectedCost, String metadata) {
        long now = System.currentTimeMillis();
        String transactionId = UUID.randomUUID().toString();
        String idempotency = playerId + ":" + type + ":" + previousRank + ":" + previousPrestige + ":"
                + targetRank + ":" + targetPrestige + ":" + transactionId;
        return new ProgressionTransactionRecord(transactionId, playerId, type, previousRank, previousPrestige,
                targetRank, targetPrestige, expectedCost, BigDecimal.ZERO, now, now,
                ProgressionTransactionState.CREATED, 0, "", ProgressionRewardStatus.PENDING,
                ProgressionExternalStatus.PENDING, idempotency, metadata);
    }

    private BigDecimal rankCost(RankDefinition current, RankDefinition target) {
        int currentIndex = ranks.indexOf(current.id());
        int targetIndex = ranks.indexOf(target.id());
        BigDecimal total = BigDecimal.ZERO;
        for (int index = currentIndex; index < targetIndex; index++) total = total.add(ranks.definitions().get(index).nextCost());
        return total;
    }

    private boolean meetsRequirements(Player player, PlayerProfile profile, String permission, List<String> requirements) {
        if (permission != null && !player.hasPermission(permission)) return false;
        for (String requirement : requirements) {
            String[] parts = requirement.toLowerCase(java.util.Locale.ROOT).split(":", 2);
            if (parts.length != 2) return false;
            switch (parts[0]) {
                case "permission" -> { if (!player.hasPermission(parts[1])) return false; }
                case "rank" -> { if (ranks.indexOf(profile.currentRank()) < ranks.indexOf(parts[1])) return false; }
                case "prestige" -> { if (prestiges.indexOf(profile.currentPrestige()) < prestiges.indexOf(parts[1])) return false; }
                default -> { return false; }
            }
        }
        return true;
    }

    private void continueRankup(ProgressionTransactionRecord transaction, RankDefinition current,
                                RankDefinition target, List<RankDefinition> crossedRanks,
                                CompletableFuture<ProgressionResult> result) {
        UUID playerId = transaction.playerId();
        Player player = Bukkit.getPlayer(playerId);
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        if (player == null || profile == null) {
            failTransaction(transaction, result, "player-offline", "Player left before rankup withdrawal");
            return;
        }
        if (!profile.currentRank().equals(transaction.previousRank())) {
            failTransaction(transaction, result, "profile-changed", "Rank changed before rankup withdrawal");
            return;
        }
        recordWithdrawalIntent(transaction).whenComplete((ignored, intentError) -> sync(() -> {
            if (intentError != null) {
                inFlight.remove(playerId);
                result.complete(ProgressionResult.failure("database-failed"));
                return;
            }
            VaultEconomyAdapter.Transaction withdrawal = economy.withdraw(player, transaction.expectedCost());
            if (!withdrawal.success()) {
                failTransaction(transaction, result, "economy-failed", withdrawal.error());
                return;
            }
            transactions.markWithdrawalConfirmed(transaction.transactionId(), transaction.expectedCost())
                    .thenCompose(done -> transactions.markProfileUpdatePending(transaction.transactionId()))
                    .whenComplete((marked, markError) -> sync(() -> {
                        if (markError != null) {
                            transactions.markWithdrawalAmbiguous(transaction.transactionId(),
                                    "Vault reported rankup withdrawal success, but confirmation persistence failed: "
                                            + rootMessage(markError));
                            inFlight.remove(playerId);
                            result.complete(ProgressionResult.failure("withdrawal-ambiguous"));
                            return;
                        }
                        profile.currentRank(transaction.targetRank());
                        profiles.save(profile).whenComplete((saved, saveError) -> sync(() -> {
                            if (saveError != null) {
                                profile.currentRank(transaction.previousRank());
                                refund(transaction, player, transaction.expectedCost(), "rankup profile save failed");
                                transactions.markFailed(transaction.transactionId(), rootMessage(saveError));
                                inFlight.remove(playerId);
                                result.complete(ProgressionResult.failure("database-failed"));
                                return;
                            }
                            afterProfileSavedRankup(transaction, current, target, crossedRanks, result);
                        }));
                    }));
        }));
    }

    private void afterProfileSavedRankup(ProgressionTransactionRecord transaction, RankDefinition current,
                                         RankDefinition target, List<RankDefinition> crossedRanks,
                                         CompletableFuture<ProgressionResult> result) {
        transactions.markProfileSaved(transaction.transactionId()).whenComplete((ignored, stateError) ->
                sync(() -> {
                    if (stateError != null) {
                        inFlight.remove(transaction.playerId());
                        result.complete(ProgressionResult.failure("database-failed"));
                        return;
                    }
                    transactions.markPermissionsUpdatePending(transaction.transactionId()).whenComplete((pending, pendingError) ->
                            sync(() -> {
                                if (pendingError != null) {
                                    inFlight.remove(transaction.playerId());
                                    result.complete(ProgressionResult.failure("database-failed"));
                                    return;
                                }
                                replaceRankGroup(transaction.playerId(), current, target).whenComplete((permissions, permissionError) -> {
                                    if (permissionError != null) {
                                        plugin.getLogger().severe("LuckPerms rank synchronization failed for "
                                                + transaction.playerId() + ": " + rootMessage(permissionError));
                                        repair(transaction.playerId(), "rankup-permission-sync", true);
                                        inFlight.remove(transaction.playerId());
                                        result.complete(new ProgressionResult(true, "success-permissions-pending",
                                                transaction.previousRank(), transaction.targetRank(),
                                                transaction.previousPrestige(), transaction.targetPrestige(),
                                                transaction.expectedCost()));
                                        return;
                                    }
                                    transactions.markPermissionsUpdated(transaction.transactionId()).whenComplete((updated, updateError) ->
                                            sync(() -> {
                                                if (updateError != null) {
                                                    inFlight.remove(transaction.playerId());
                                                    result.complete(ProgressionResult.failure("database-failed"));
                                                    return;
                                                }
                                                deliverRankupRewards(transaction, current, target, crossedRanks, result);
                                            }));
                                });
                            }));
                }));
    }

    private CompletableFuture<Void> recordWithdrawalIntent(ProgressionTransactionRecord transaction) {
        return transactions.markValidated(transaction.transactionId())
                .thenCompose(ignored -> transactions.markWithdrawalIntent(transaction.transactionId()))
                .thenCompose(ignored -> transactions.markWithdrawalInProgress(transaction.transactionId()));
    }

    private void deliverRankupRewards(ProgressionTransactionRecord transaction, RankDefinition current,
                                      RankDefinition target, List<RankDefinition> crossedRanks,
                                      CompletableFuture<ProgressionResult> result) {
        Player player = Bukkit.getPlayer(transaction.playerId());
        PlayerProfile profile = profiles.cachedProfile(transaction.playerId()).orElse(null);
        if (player == null || profile == null) {
            manualReview(transaction, result, "reward-delivery-offline", "Player offline before rankup rewards");
            return;
        }
        transactions.markRewardsPending(transaction.transactionId())
                .thenCompose(ignored -> transactions.reserveRewards(transaction.transactionId()))
                .whenComplete((reserved, reserveError) ->
                sync(() -> {
                    if (reserveError != null) {
                        inFlight.remove(transaction.playerId());
                        result.complete(ProgressionResult.failure("database-failed"));
                        return;
                    }
                    if (!reserved) {
                        completeRankup(transaction, result, "success");
                        return;
                    }
                    List<RewardLedgerService.ComponentDraft> drafts = new ArrayList<>();
                    addCommandDrafts(drafts, current.leaveCommands(), "leave-" + current.id(), player,
                            transaction.previousRank(), transaction.targetRank(), transaction.targetPrestige());
                    for (int index = 0; index < crossedRanks.size(); index++) {
                        RankDefinition entered = crossedRanks.get(index);
                        addCommandDrafts(drafts, entered.enterCommands(), "enter-" + entered.id(), player,
                                transaction.previousRank(), entered.id(), transaction.targetPrestige());
                        if (index + 1 < crossedRanks.size()) {
                            addCommandDrafts(drafts, entered.leaveCommands(), "leave-" + entered.id(), player,
                                    entered.id(), crossedRanks.get(index + 1).id(), transaction.targetPrestige());
                        }
                    }
                    addRankupAnnouncementDraft(drafts, player, current, target);
                    createProgressionRewardPackage(transaction, drafts).whenComplete((createdPackage, packageError) ->
                            sync(() -> {
                                if (packageError != null) {
                                    transactions.markManualReview(transaction.transactionId(),
                                            "Progression reward package creation failed: " + rootMessage(packageError));
                                    inFlight.remove(transaction.playerId());
                                    result.complete(ProgressionResult.failure("reward-package-failed"));
                                    return;
                                }
                                sendRankupFeedback(player, current, target, transaction.expectedCost());
                                if (plugin.statistics() != null) plugin.statistics().recordRankup(transaction.playerId());
                                if (plugin.gangs() != null) plugin.gangs().recordContribution(transaction.playerId(),
                                        site.mcrelicworld.relicprison.gang.GangConfig.ContributionType.RANKUPS,
                                        BigDecimal.ONE, "", "rankup:" + transaction.transactionId())
                                        .exceptionally(error -> null);
                                if (plugin.multiplierService() != null) plugin.multiplierService().invalidate(transaction.playerId());
                                transactions.markRewardsQueued(transaction.transactionId())
                                        .thenCompose(ignored -> plugin.rewardLedger().deliverDueAsync())
                                        .whenComplete((queued, queueError) -> sync(() ->
                                                completeRankup(transaction, result, queueError == null
                                                        ? "success-rewards-queued" : "success-rewards-pending")));
                            }));
                }));
    }

    private void completeRankup(ProgressionTransactionRecord transaction, CompletableFuture<ProgressionResult> result,
                                String message) {
        transactions.markCompleted(transaction.transactionId()).whenComplete((ignored, error) ->
                sync(() -> {
                    if (error != null) {
                        plugin.getLogger().warning("Unable to mark rankup transaction completed: " + rootMessage(error));
                    }
                    inFlight.remove(transaction.playerId());
                    result.complete(new ProgressionResult(true, message, transaction.previousRank(),
                            transaction.targetRank(), transaction.previousPrestige(), transaction.targetPrestige(),
                            transaction.expectedCost()));
                }));
    }

    private void continuePrestige(ProgressionTransactionRecord transaction, RankDefinition currentRank,
                                  RankDefinition startingRank, PrestigeDefinition next,
                                  CompletableFuture<ProgressionResult> result) {
        UUID playerId = transaction.playerId();
        Player player = Bukkit.getPlayer(playerId);
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        if (player == null || profile == null) {
            failTransaction(transaction, result, "player-offline", "Player left before prestige withdrawal");
            return;
        }
        if (!profile.currentRank().equals(transaction.previousRank())
                || !java.util.Objects.equals(profile.currentPrestige(), transaction.previousPrestige())) {
            failTransaction(transaction, result, "profile-changed", "Profile changed before prestige withdrawal");
            return;
        }
        recordWithdrawalIntent(transaction).whenComplete((ignored, intentError) -> sync(() -> {
            if (intentError != null) {
                inFlight.remove(playerId);
                result.complete(ProgressionResult.failure("database-failed"));
                return;
            }
            VaultEconomyAdapter.Transaction withdrawal = economy.withdraw(player, transaction.expectedCost());
            if (!withdrawal.success()) {
                failTransaction(transaction, result, "economy-failed", withdrawal.error());
                return;
            }
            transactions.markWithdrawalConfirmed(transaction.transactionId(), transaction.expectedCost())
                    .thenCompose(done -> transactions.markProfileUpdatePending(transaction.transactionId()))
                    .whenComplete((marked, markError) -> sync(() -> {
                        if (markError != null) {
                            transactions.markWithdrawalAmbiguous(transaction.transactionId(),
                                    "Vault reported prestige withdrawal success, but confirmation persistence failed: "
                                            + rootMessage(markError));
                            inFlight.remove(playerId);
                            result.complete(ProgressionResult.failure("withdrawal-ambiguous"));
                            return;
                        }
                        profile.currentPrestige(transaction.targetPrestige());
                        profile.currentRank(transaction.targetRank());
                        profiles.save(profile).whenComplete((saved, saveError) -> sync(() -> {
                            if (saveError != null) {
                                profile.currentPrestige(transaction.previousPrestige());
                                profile.currentRank(transaction.previousRank());
                                refund(transaction, player, transaction.expectedCost(), "prestige profile save failed");
                                transactions.markFailed(transaction.transactionId(), rootMessage(saveError));
                                inFlight.remove(playerId);
                                result.complete(ProgressionResult.failure("database-failed"));
                                return;
                            }
                            afterProfileSavedPrestige(transaction, currentRank, startingRank, next, result);
                        }));
                    }));
        }));
    }

    private void afterProfileSavedPrestige(ProgressionTransactionRecord transaction, RankDefinition currentRank,
                                           RankDefinition startingRank, PrestigeDefinition next,
                                           CompletableFuture<ProgressionResult> result) {
        transactions.markProfileSaved(transaction.transactionId()).whenComplete((ignored, stateError) ->
                sync(() -> {
                    if (stateError != null) {
                        inFlight.remove(transaction.playerId());
                        result.complete(ProgressionResult.failure("database-failed"));
                        return;
                    }
                    transactions.markPermissionsUpdatePending(transaction.transactionId()).whenComplete((pending, pendingError) ->
                            sync(() -> {
                                if (pendingError != null) {
                                    inFlight.remove(transaction.playerId());
                                    result.complete(ProgressionResult.failure("database-failed"));
                                    return;
                                }
                                replaceProgressionGroups(transaction.playerId(), currentRank, startingRank,
                                        transaction.previousPrestige(), next).whenComplete((permissions, permissionError) -> {
                                    if (permissionError != null) {
                                        plugin.getLogger().severe("LuckPerms prestige synchronization failed for "
                                                + transaction.playerId() + ": " + rootMessage(permissionError));
                                        repair(transaction.playerId(), "prestige-permission-sync", true);
                                        inFlight.remove(transaction.playerId());
                                        result.complete(new ProgressionResult(true, "success-permissions-pending",
                                                transaction.previousRank(), transaction.targetRank(),
                                                transaction.previousPrestige(), transaction.targetPrestige(),
                                                transaction.expectedCost()));
                                        return;
                                    }
                                    transactions.markPermissionsUpdated(transaction.transactionId()).whenComplete((updated, updateError) ->
                                            sync(() -> {
                                                if (updateError != null) {
                                                    inFlight.remove(transaction.playerId());
                                                    result.complete(ProgressionResult.failure("database-failed"));
                                                    return;
                                                }
                                                deliverPrestigeRewards(transaction, next, result);
                                            }));
                                });
                            }));
                }));
    }

    private void deliverPrestigeRewards(ProgressionTransactionRecord transaction, PrestigeDefinition next,
                                        CompletableFuture<ProgressionResult> result) {
        Player player = Bukkit.getPlayer(transaction.playerId());
        if (player == null) {
            manualReview(transaction, result, "reward-delivery-offline", "Player offline before prestige rewards");
            return;
        }
        transactions.markRewardsPending(transaction.transactionId())
                .thenCompose(ignored -> transactions.reserveRewards(transaction.transactionId()))
                .whenComplete((reserved, reserveError) ->
                sync(() -> {
                    if (reserveError != null) {
                        inFlight.remove(transaction.playerId());
                        result.complete(ProgressionResult.failure("database-failed"));
                        return;
                    }
                    if (!reserved) {
                        completePrestige(transaction, result, "success");
                        return;
                    }
                    List<RewardLedgerService.ComponentDraft> drafts = new ArrayList<>();
                    prestiges.definition(transaction.previousPrestige()).ifPresent(old -> addCommandDrafts(drafts,
                            old.leaveCommands(), "leave-prestige-" + old.id(), player, transaction.previousRank(),
                            transaction.targetRank(), transaction.targetPrestige()));
                    addCommandDrafts(drafts, next.enterCommands(), "enter-prestige-" + next.id(), player,
                            transaction.previousRank(), transaction.targetRank(), transaction.targetPrestige());
                    addPrestigeAnnouncementDraft(drafts, player, next);
                    createProgressionRewardPackage(transaction, drafts).whenComplete((createdPackage, packageError) ->
                            sync(() -> {
                                if (packageError != null) {
                                    transactions.markManualReview(transaction.transactionId(),
                                            "Progression reward package creation failed: " + rootMessage(packageError));
                                    inFlight.remove(transaction.playerId());
                                    result.complete(ProgressionResult.failure("reward-package-failed"));
                                    return;
                                }
                                sendPrestigeFeedback(player, next, transaction.expectedCost());
                                if (plugin.statistics() != null) plugin.statistics().recordPrestige(transaction.playerId());
                                if (plugin.gangs() != null) plugin.gangs().recordContribution(transaction.playerId(),
                                        site.mcrelicworld.relicprison.gang.GangConfig.ContributionType.PRESTIGES,
                                        BigDecimal.ONE, "", "prestige:" + transaction.transactionId())
                                        .exceptionally(error -> null);
                                if (plugin.multiplierService() != null) plugin.multiplierService().invalidate(transaction.playerId());
                                transactions.markRewardsQueued(transaction.transactionId())
                                        .thenCompose(ignored -> plugin.rewardLedger().deliverDueAsync())
                                        .whenComplete((queued, queueError) -> sync(() ->
                                                completePrestige(transaction, result, queueError == null
                                                        ? "success-rewards-queued" : "success-rewards-pending")));
                            }));
                }));
    }

    private void completePrestige(ProgressionTransactionRecord transaction, CompletableFuture<ProgressionResult> result,
                                  String message) {
        transactions.markCompleted(transaction.transactionId()).whenComplete((ignored, error) ->
                sync(() -> {
                    if (error != null) {
                        plugin.getLogger().warning("Unable to mark prestige transaction completed: " + rootMessage(error));
                    }
                    inFlight.remove(transaction.playerId());
                    result.complete(new ProgressionResult(true, message, transaction.previousRank(),
                            transaction.targetRank(), transaction.previousPrestige(), transaction.targetPrestige(),
                            transaction.expectedCost()));
                }));
    }

    private void failTransaction(ProgressionTransactionRecord transaction, CompletableFuture<ProgressionResult> result,
                                 String code, String summary) {
        transactions.markFailed(transaction.transactionId(), summary).whenComplete((ignored, error) ->
                sync(() -> {
                    if (error != null) plugin.getLogger().warning("Unable to mark progression transaction failed: "
                            + rootMessage(error));
                    inFlight.remove(transaction.playerId());
                    result.complete(ProgressionResult.failure(code));
                }));
    }

    private void manualReview(ProgressionTransactionRecord transaction, CompletableFuture<ProgressionResult> result,
                              String code, String summary) {
        transactions.markManualReview(transaction.transactionId(), summary).whenComplete((ignored, error) ->
                sync(() -> {
                    if (error != null) plugin.getLogger().warning("Unable to mark progression transaction for review: "
                            + rootMessage(error));
                    inFlight.remove(transaction.playerId());
                    result.complete(ProgressionResult.failure(code));
                }));
    }

    private CompletableFuture<Void> refund(ProgressionTransactionRecord transaction, Player player,
                                           BigDecimal amount, String reason) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        if (safeAmount.signum() <= 0) return CompletableFuture.completedFuture(null);
        return transactions.recordRefundIntent(transaction.transactionId(), safeAmount, reason)
                .thenCompose(ignored -> transactions.claimRefund(transaction.transactionId()))
                .thenCompose(claimed -> {
                    if (!claimed) return CompletableFuture.completedFuture(null);
                    CompletableFuture<Void> result = new CompletableFuture<>();
                    sync(() -> {
                        try {
                            VaultEconomyAdapter.Transaction refund = economy.deposit(player, safeAmount);
                            CompletableFuture<Void> persisted = refund.success()
                                    ? transactions.markRefundConfirmed(transaction.transactionId())
                                    : transactions.markRefundFailed(transaction.transactionId(), refund.error());
                            persisted.whenComplete((saved, error) -> {
                                if (error != null) {
                                    transactions.markRefundAmbiguous(transaction.transactionId(),
                                            "Vault refund returned but result persistence failed: "
                                                    + rootMessage(error));
                                    result.completeExceptionally(error);
                                    return;
                                }
                                if (!refund.success()) {
                                    plugin.getLogger().severe("Progression refund failed for "
                                            + player.getUniqueId() + " after " + reason + ": "
                                            + refund.error());
                                }
                                result.complete(null);
                            });
                        } catch (RuntimeException error) {
                            transactions.markRefundAmbiguous(transaction.transactionId(),
                                    "Vault refund outcome is ambiguous: " + rootMessage(error));
                            result.completeExceptionally(error);
                        }
                    });
                    return result;
                });
    }

    public CompletableFuture<ProgressionResult> setRank(UUID playerId, String rankId) {
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        RankDefinition target = ranks.definition(rankId).orElse(null);
        if (profile == null || target == null) {
            return CompletableFuture.completedFuture(ProgressionResult.failure("invalid-player-or-rank"));
        }
        RankDefinition previous = ranks.definition(profile.currentRank()).orElse(null);
        String old = profile.currentRank();
        profile.currentRank(target.id());
        CompletableFuture<ProgressionResult> result = new CompletableFuture<>();
        profiles.save(profile).whenComplete((ignored, saveError) -> {
            if (saveError != null) {
                profile.currentRank(old);
                result.completeExceptionally(saveError);
                return;
            }
            replaceRankGroup(playerId, previous, target).whenComplete((permissions, permissionError) -> {
                if (permissionError != null) {
                    plugin.getLogger().warning("Admin rank LuckPerms synchronization is pending for " + playerId
                            + ": " + rootMessage(permissionError));
                    repair(playerId, "admin-rank-set", false);
                }
                if (plugin.multiplierService() != null) plugin.multiplierService().invalidate(playerId);
                result.complete(new ProgressionResult(true, permissionError == null ? "admin-set" : "admin-set-permissions-pending",
                        old, target.id(), profile.currentPrestige(), profile.currentPrestige(), BigDecimal.ZERO));
            });
        });
        return result;
    }

    public CompletableFuture<ProgressionResult> setPrestige(UUID playerId, String prestigeId) {
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        PrestigeDefinition target = prestigeId == null ? null : prestiges.definition(prestigeId).orElse(null);
        if (profile == null || (prestigeId != null && target == null)) {
            return CompletableFuture.completedFuture(ProgressionResult.failure("invalid-player-or-prestige"));
        }
        String old = profile.currentPrestige();
        profile.currentPrestige(target == null ? null : target.id());
        CompletableFuture<ProgressionResult> result = new CompletableFuture<>();
        profiles.save(profile).whenComplete((ignored, saveError) -> {
            if (saveError != null) {
                profile.currentPrestige(old);
                result.completeExceptionally(saveError);
                return;
            }
            repair(playerId, "admin-prestige-set", false).whenComplete((repaired, repairError) -> {
                if (repairError != null) {
                    plugin.getLogger().warning("Admin prestige LuckPerms synchronization is pending for " + playerId
                            + ": " + rootMessage(repairError));
                }
                if (plugin.multiplierService() != null) plugin.multiplierService().invalidate(playerId);
                result.complete(new ProgressionResult(true, repairError == null ? "admin-set" : "admin-set-permissions-pending",
                        profile.currentRank(), profile.currentRank(), old, profile.currentPrestige(), BigDecimal.ZERO));
            });
        });
        return result;
    }

    @Override public CompletableFuture<Void> repair(UUID playerId) {
        return repair(playerId, "api", false);
    }

    public CompletableFuture<List<ProgressionTransactionRecord>> pendingTransactions(int limit) {
        return transactions.pending(limit);
    }

    public CompletableFuture<Optional<ProgressionTransactionRecord>> transaction(String transactionId) {
        return transactions.findById(transactionId);
    }

    public CompletableFuture<ProgressionResult> retryTransaction(String transactionId) {
        return transactions.findById(transactionId).thenCompose(record -> record
                .map(value -> recoverTransaction(value, false))
                .orElseGet(() -> CompletableFuture.completedFuture(ProgressionResult.failure("unknown-transaction"))));
    }

    public CompletableFuture<Void> recoverPending(UUID playerId, String reason) {
        return transactions.pendingFor(playerId).thenCompose(records -> {
            CompletableFuture<?>[] futures = records.stream()
                    .map(record -> recoverTransaction(record, true))
                    .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(futures);
        }).exceptionally(error -> {
            plugin.getLogger().warning("Progression recovery failed for " + playerId + " during "
                    + reason + ": " + rootMessage(error));
            return null;
        });
    }

    public CompletableFuture<Void> repair(UUID playerId, String reason, boolean automatic) {
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        if (profile == null) return CompletableFuture.failedFuture(new IllegalStateException("Player profile is not loaded"));
        RankDefinition fallback = ranks.definition(plugin.config().snapshot().progression().startingRank()).orElse(ranks.first());
        String previousRank = profile.currentRank();
        String previousPrestige = profile.currentPrestige();
        RankDefinition rank = ranks.definition(profile.currentRank()).orElse(fallback);
        PrestigeDefinition prestige = prestiges.definition(profile.currentPrestige()).orElse(null);
        List<String> repairs = new ArrayList<>();
        if (!rank.id().equals(profile.currentRank())) {
            profile.currentRank(rank.id());
            repairs.add("rank profile value repaired from " + previousRank + " to " + rank.id());
        }
        if (profile.currentPrestige() != null && prestige == null) {
            profile.currentPrestige(null);
            repairs.add("prestige profile value cleared from " + previousPrestige);
        }
        Collection<String> managed = managedGroups();
        Collection<String> managedRanks = managedRankGroups();
        Collection<String> managedPrestiges = managedPrestigeGroups();
        Set<String> desired = new HashSet<>();
        desired.add(rank.luckPermsGroup());
        if (prestige != null) desired.add(prestige.luckPermsGroup());
        Set<String> desiredRanks = Set.of(rank.luckPermsGroup().toLowerCase(java.util.Locale.ROOT));
        Set<String> desiredPrestiges = prestige == null ? Set.of()
                : Set.of(prestige.luckPermsGroup().toLowerCase(java.util.Locale.ROOT));
        CompletableFuture<Void> save = profile.dirty() ? profiles.save(profile) : CompletableFuture.completedFuture(null);
        return luckPerms.snapshotManagedGroups(playerId, managedRanks, managedPrestiges).thenCompose(previous ->
                save.thenCompose(saved -> luckPerms.repairGroups(playerId, managed, desired)
                        .thenCompose(repaired -> callRepairEvent(playerId, profile.lastName(), previous,
                                rank.id(), prestige == null ? null : prestige.id(),
                                repairList(repairs, previous, desiredRanks, desiredPrestiges), reason, automatic))));
    }

    private CompletableFuture<Void> callRepairEvent(UUID playerId, String lastName,
                                                    LuckPermsIntegration.GroupSnapshot previous,
                                                    String expectedRank, String expectedPrestige,
                                                    List<String> repairs, String reason, boolean automatic) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.getPluginManager().callEvent(new RelicPlayerProgressionRepairEvent(playerId, lastName,
                        previous.rankGroups(), previous.prestigeGroups(), expectedRank, expectedPrestige,
                        repairs, reason, automatic));
                result.complete(null);
            } catch (RuntimeException ex) {
                result.completeExceptionally(ex);
            }
        });
        return result;
    }

    private static List<String> repairList(List<String> profileRepairs, LuckPermsIntegration.GroupSnapshot previous,
                                           Set<String> desiredRanks, Set<String> desiredPrestiges) {
        List<String> repairs = new ArrayList<>(profileRepairs);
        if (!previous.rankGroups().equals(desiredRanks)) {
            repairs.add("rank permission groups repaired from " + previous.rankGroups() + " to " + desiredRanks);
        }
        if (!previous.prestigeGroups().equals(desiredPrestiges)) {
            repairs.add("prestige permission groups repaired from " + previous.prestigeGroups() + " to " + desiredPrestiges);
        }
        return List.copyOf(repairs);
    }

    public BigDecimal nextRankCost(UUID playerId) {
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        if (profile == null) return BigDecimal.ZERO;
        RankDefinition current = ranks.definition(profile.currentRank()).orElse(null);
        if (current == null || ranks.next(current.id()).isEmpty()) return BigDecimal.ZERO;
        return current.nextCost().multiply(prestiges.rankCostMultiplier(profile.currentPrestige()));
    }

    private CompletableFuture<Void> replaceRankGroup(UUID playerId, RankDefinition oldRank, RankDefinition newRank) {
        return luckPerms.replaceGroups(playerId, managedRankGroups(), oldRank == null ? null : oldRank.luckPermsGroup(),
                newRank == null ? null : newRank.luckPermsGroup());
    }

    private CompletableFuture<ProgressionResult> recoverTransaction(ProgressionTransactionRecord record,
                                                                    boolean automatic) {
        if (!inFlight.add(record.playerId())) {
            return CompletableFuture.completedFuture(ProgressionResult.failure("already-processing"));
        }
        CompletableFuture<ProgressionResult> result = new CompletableFuture<>();
        sync(() -> recoverTransactionSync(record, automatic, result));
        return result.whenComplete((ignored, error) -> inFlight.remove(record.playerId()));
    }

    private void recoverTransactionSync(ProgressionTransactionRecord record, boolean automatic,
                                        CompletableFuture<ProgressionResult> result) {
        if (record.state().terminal()) {
            result.complete(ProgressionResult.failure("terminal-transaction"));
            return;
        }
        if (record.rewardStatus() == ProgressionRewardStatus.RESERVED) {
            transactions.markManualReview(record.transactionId(),
                    "Reward delivery was reserved before shutdown; staff must verify external rewards.")
                    .whenComplete((ignored, error) -> result.complete(ProgressionResult.failure("manual-review")));
            return;
        }
        PlayerProfile profile = profiles.cachedProfile(record.playerId()).orElse(null);
        Player player = Bukkit.getPlayer(record.playerId());
        if (profile == null) {
            transactions.markManualReview(record.transactionId(), "Profile is not loaded for recovery")
                    .whenComplete((ignored, error) -> result.complete(ProgressionResult.failure("profile-loading")));
            return;
        }
        transactions.incrementRecovery(record.transactionId());
        if (record.state() == ProgressionTransactionState.STARTED
                || record.state() == ProgressionTransactionState.CREATED
                || record.state() == ProgressionTransactionState.VALIDATED
                || record.state() == ProgressionTransactionState.WITHDRAWAL_INTENT_RECORDED) {
            transactions.markFailed(record.transactionId(), "No withdrawal was recorded; safe for player to retry.")
                    .whenComplete((ignored, error) -> result.complete(ProgressionResult.failure("safe-to-retry")));
            return;
        }
        if (record.state().withdrawalAmbiguous()) {
            transactions.markWithdrawalAmbiguous(record.transactionId(),
                    "Generic Vault withdrawal was in progress when recovery started; staff must inspect economy history.")
                    .whenComplete((ignored, error) -> result.complete(ProgressionResult.failure("withdrawal-ambiguous")));
            return;
        }
        if (record.state().withdrawalConfirmed() && !profileAtTargetOrNewer(record, profile)) {
            if (player == null) {
                transactions.markManualReview(record.transactionId(), "Money withdrawn, profile not advanced, player offline")
                        .whenComplete((ignored, error) -> result.complete(ProgressionResult.failure("manual-review")));
                return;
            }
            refund(record, player, record.actualWithdrawn().signum() > 0 ? record.actualWithdrawn() : record.expectedCost(),
                    "progression recovery");
            transactions.markFailed(record.transactionId(), "Withdrawal refunded during recovery")
                    .whenComplete((ignored, error) -> result.complete(ProgressionResult.failure("refunded")));
            return;
        }
        if (!profileAtTargetOrNewer(record, profile)) {
            transactions.markManualReview(record.transactionId(),
                    "Profile does not match transaction target; recovery will not downgrade or overwrite.")
                    .whenComplete((ignored, error) -> result.complete(ProgressionResult.failure("manual-review")));
            return;
        }
        if (profileNewerThanTarget(record, profile)) {
            transactions.markRewardsSkipped(record.transactionId())
                    .thenCompose(ignored -> transactions.markCompleted(record.transactionId()))
                    .whenComplete((ignored, error) -> result.complete(new ProgressionResult(true,
                            "completed-newer-profile", record.previousRank(), profile.currentRank(),
                            record.previousPrestige(), profile.currentPrestige(), record.expectedCost())));
            return;
        }
        CompletableFuture<Void> profileState = record.state() == ProgressionTransactionState.MONEY_WITHDRAWN
                || record.state() == ProgressionTransactionState.WITHDRAWAL_CONFIRMED
                || record.state() == ProgressionTransactionState.PROFILE_UPDATE_PENDING
                ? transactions.markProfileSaved(record.transactionId())
                : CompletableFuture.completedFuture(null);
        profileState.whenComplete((ignored, stateError) -> {
            if (stateError != null) {
                result.complete(ProgressionResult.failure("database-failed"));
                return;
            }
            if (record.operationType() == ProgressionOperationType.RANKUP) {
                recoverRankupAfterProfile(record, automatic, result);
            } else {
                recoverPrestigeAfterProfile(record, automatic, result);
            }
        });
    }

    private void recoverRankupAfterProfile(ProgressionTransactionRecord record, boolean automatic,
                                           CompletableFuture<ProgressionResult> result) {
        RankDefinition oldRank = ranks.definition(record.previousRank()).orElse(null);
        RankDefinition target = ranks.definition(record.targetRank()).orElse(null);
        if (target == null) {
            transactions.markManualReview(record.transactionId(), "Recovered rank target no longer exists")
                    .whenComplete((ignored, error) -> result.complete(ProgressionResult.failure("manual-review")));
            return;
        }
        replaceRankGroup(record.playerId(), oldRank, target).whenComplete((permissions, permissionError) -> {
            if (permissionError != null) {
                result.complete(ProgressionResult.failure("luckperms-failed"));
                return;
            }
            transactions.markPermissionsUpdated(record.transactionId()).whenComplete((updated, updateError) ->
                    sync(() -> {
                        if (updateError != null) {
                            result.complete(ProgressionResult.failure("database-failed"));
                            return;
                        }
                        int oldIndex = oldRank == null ? ranks.indexOf(record.previousRank()) : ranks.indexOf(oldRank.id());
                        int targetIndex = ranks.indexOf(target.id());
                        List<RankDefinition> crossed = oldIndex >= 0 && targetIndex >= oldIndex
                                ? List.copyOf(ranks.definitions().subList(oldIndex + 1, targetIndex + 1))
                                : List.of(target);
                        deliverRankupRewards(record, oldRank == null ? target : oldRank, target, crossed, result);
                    }));
        });
    }

    private void recoverPrestigeAfterProfile(ProgressionTransactionRecord record, boolean automatic,
                                             CompletableFuture<ProgressionResult> result) {
        RankDefinition oldRank = ranks.definition(record.previousRank()).orElse(ranks.last());
        RankDefinition targetRank = ranks.definition(record.targetRank()).orElse(ranks.first());
        PrestigeDefinition targetPrestige = prestiges.definition(record.targetPrestige()).orElse(null);
        if (targetPrestige == null) {
            transactions.markManualReview(record.transactionId(), "Recovered prestige target no longer exists")
                    .whenComplete((ignored, error) -> result.complete(ProgressionResult.failure("manual-review")));
            return;
        }
        replaceProgressionGroups(record.playerId(), oldRank, targetRank, record.previousPrestige(), targetPrestige)
                .whenComplete((permissions, permissionError) -> {
                    if (permissionError != null) {
                        result.complete(ProgressionResult.failure("luckperms-failed"));
                        return;
                    }
                    transactions.markPermissionsUpdated(record.transactionId()).whenComplete((updated, updateError) ->
                            sync(() -> {
                                if (updateError != null) {
                                    result.complete(ProgressionResult.failure("database-failed"));
                                    return;
                                }
                                deliverPrestigeRewards(record, targetPrestige, result);
                            }));
                });
    }

    private boolean profileAtTargetOrNewer(ProgressionTransactionRecord record, PlayerProfile profile) {
        return !profileNewerComparison(record, profile).equals(Comparison.BEFORE);
    }

    private boolean profileNewerThanTarget(ProgressionTransactionRecord record, PlayerProfile profile) {
        return profileNewerComparison(record, profile).equals(Comparison.AFTER);
    }

    private Comparison profileNewerComparison(ProgressionTransactionRecord record, PlayerProfile profile) {
        if (record.operationType() == ProgressionOperationType.PRESTIGE) {
            int currentPrestige = prestiges.indexOf(profile.currentPrestige());
            int targetPrestige = prestiges.indexOf(record.targetPrestige());
            if (currentPrestige > targetPrestige) return Comparison.AFTER;
            if (currentPrestige < targetPrestige) return Comparison.BEFORE;
            int currentRank = ranks.indexOf(profile.currentRank());
            int targetRank = ranks.indexOf(record.targetRank());
            if (currentRank > targetRank) return Comparison.AFTER;
            if (currentRank < targetRank) return Comparison.BEFORE;
            return Comparison.EQUAL;
        }
        if (!java.util.Objects.equals(profile.currentPrestige(), record.targetPrestige())) {
            int currentPrestige = prestiges.indexOf(profile.currentPrestige());
            int targetPrestige = prestiges.indexOf(record.targetPrestige());
            if (currentPrestige > targetPrestige) return Comparison.AFTER;
            if (currentPrestige < targetPrestige) return Comparison.BEFORE;
        }
        int currentRank = ranks.indexOf(profile.currentRank());
        int targetRank = ranks.indexOf(record.targetRank());
        if (currentRank > targetRank) return Comparison.AFTER;
        if (currentRank < targetRank) return Comparison.BEFORE;
        return Comparison.EQUAL;
    }

    private CompletableFuture<Void> replaceProgressionGroups(UUID playerId, RankDefinition oldRank, RankDefinition newRank,
                                                             String oldPrestigeId, PrestigeDefinition newPrestige) {
        PrestigeDefinition oldPrestige = prestiges.definition(oldPrestigeId).orElse(null);
        return luckPerms.replaceGroups(playerId, managedRankGroups(), oldRank.luckPermsGroup(), newRank.luckPermsGroup())
                .thenCompose(ignored -> luckPerms.replaceGroups(playerId, managedPrestigeGroups(),
                        oldPrestige == null ? null : oldPrestige.luckPermsGroup(), newPrestige.luckPermsGroup()));
    }

    private Collection<String> managedRankGroups() {
        return ranks.definitions().stream().map(RankDefinition::luckPermsGroup).toList();
    }

    private Collection<String> managedPrestigeGroups() {
        return prestiges.definitions().stream().map(PrestigeDefinition::luckPermsGroup).toList();
    }

    private Collection<String> managedGroups() {
        List<String> result = new ArrayList<>();
        ranks.definitions().forEach(rank -> result.add(rank.luckPermsGroup()));
        prestiges.definitions().forEach(prestige -> result.add(prestige.luckPermsGroup()));
        return result;
    }

    private void addCommandDrafts(List<RewardLedgerService.ComponentDraft> drafts, List<String> commands,
                                  String componentPrefix, Player player, String oldRank, String newRank,
                                  String prestige) {
        long now = System.currentTimeMillis();
        for (int index = 0; index < commands.size(); index++) {
            String configured = commands.get(index);
            CommandDraft parsed = parseProgressionCommand(configured, player, oldRank, newRank, prestige);
            if (parsed == null) continue;
            String componentId = componentPrefix + "-command-" + index;
            long dueAt = now + parsed.delayTicks() * 50L;
            if (parsed.playerCommand()) {
                drafts.add(RewardLedgerService.ComponentDraft.playerCommand(componentId, parsed.command(), dueAt));
            } else {
                drafts.add(RewardLedgerService.ComponentDraft.consoleCommand(componentId, parsed.command(), dueAt));
            }
        }
    }

    private CommandDraft parseProgressionCommand(String configured, Player player, String oldRank, String newRank,
                                                 String prestige) {
        String command = configured.replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%old_rank%", oldRank == null ? "" : oldRank)
                .replace("%new_rank%", newRank == null ? "" : newRank)
                .replace("%prestige%", prestige == null ? "none" : prestige);
        long delayTicks = 0L;
        if (command.regionMatches(true, 0, "delay:", 0, "delay:".length())) {
            int separator = command.indexOf(':', "delay:".length());
            if (separator < 0) {
                plugin.getLogger().warning("Invalid delayed progression command: " + configured);
                return null;
            }
            try {
                delayTicks = Long.parseLong(command.substring("delay:".length(), separator).trim());
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Invalid progression command delay: " + configured);
                return null;
            }
            if (delayTicks < 0L || delayTicks > 72_000L) {
                plugin.getLogger().warning("Progression command delay must be between 0 and 72000 ticks: " + configured);
                return null;
            }
            command = command.substring(separator + 1).trim();
        }
        if (command.regionMatches(true, 0, "player:", 0, "player:".length())) {
            return new CommandDraft(command.substring("player:".length()).trim(), true, delayTicks);
        }
        if (command.regionMatches(true, 0, "console:", 0, "console:".length())) {
            command = command.substring("console:".length()).trim();
        }
        return command.isBlank() ? null : new CommandDraft(command, false, delayTicks);
    }

    private void addRankupAnnouncementDraft(List<RewardLedgerService.ComponentDraft> drafts, Player player,
                                            RankDefinition oldRank, RankDefinition newRank) {
        if (!plugin.config().snapshot().progression().broadcastRankups()) return;
        String message = ColorUtil.color("&8[&5RelicPrison&8] &f" + player.getName() + " &7ranked up to &d"
                + newRank.displayName() + "&7!");
        drafts.add(RewardLedgerService.ComponentDraft.announcement("rankup-announcement", message,
                System.currentTimeMillis()));
    }

    private void addPrestigeAnnouncementDraft(List<RewardLedgerService.ComponentDraft> drafts, Player player,
                                              PrestigeDefinition prestige) {
        if (!plugin.config().snapshot().progression().broadcastPrestiges()) return;
        String message = ColorUtil.color("&8[&5RelicPrison&8] &f" + player.getName() + " &7reached &d"
                + prestige.displayName() + " Prestige&7!");
        drafts.add(RewardLedgerService.ComponentDraft.announcement("prestige-announcement", message,
                System.currentTimeMillis()));
    }

    private CompletableFuture<Boolean> createProgressionRewardPackage(ProgressionTransactionRecord transaction,
                                                                      List<RewardLedgerService.ComponentDraft> drafts) {
        if (plugin.rewardLedger() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Reward ledger is not available"));
        }
        String packageId = "progression-" + transaction.transactionId();
        String payload = "{\"operation\":\"" + transaction.operationType().name() + "\","
                + "\"transaction\":\"" + transaction.transactionId() + "\","
                + "\"previous_rank\":\"" + safeJson(transaction.previousRank()) + "\","
                + "\"target_rank\":\"" + safeJson(transaction.targetRank()) + "\","
                + "\"previous_prestige\":\"" + safeJson(transaction.previousPrestige()) + "\","
                + "\"target_prestige\":\"" + safeJson(transaction.targetPrestige()) + "\","
                + "\"cost\":\"" + transaction.expectedCost().toPlainString() + "\"}";
        return plugin.rewardLedger().createPackage(packageId, "progression", transaction.transactionId(),
                transaction.playerId(), payload, drafts);
    }

    private void sendRankupFeedback(Player player, RankDefinition oldRank, RankDefinition newRank, BigDecimal cost) {
        Map<String, String> placeholders = Map.of("old_rank", oldRank.displayName(),
                "new_rank", newRank.displayName(), "cost", plugin.numbers().currency(cost));
        if (player.isOnline()) {
            plugin.messages().send(player, "rankup-success", placeholders);
            if (plugin.config().snapshot().progression().titleNotifications()) {
                ColorUtil.showTitle(player, plugin.messages().formatPlain("rankup-title", placeholders),
                        plugin.messages().formatPlain("rankup-subtitle", placeholders), 5, 35, 10);
            }
            if (plugin.config().snapshot().progression().soundNotifications()) {
                player.playSound(player.getLocation(), plugin.config().snapshot().progression().rankupSound(), 0.8f, 1.15f);
            }
        }
    }

    private void sendPrestigeFeedback(Player player, PrestigeDefinition prestige, BigDecimal cost) {
        RankDefinition startingRank = ranks.definition(plugin.config().snapshot().progression().startingRank()).orElse(ranks.first());
        Map<String, String> placeholders = Map.of("prestige", prestige.displayName(),
                "cost", plugin.numbers().currency(cost), "rank", startingRank.displayName());
        if (player.isOnline()) {
            plugin.messages().send(player, "prestige-success", placeholders);
            if (plugin.config().snapshot().progression().titleNotifications()) {
                ColorUtil.showTitle(player, plugin.messages().formatPlain("prestige-title", placeholders),
                        plugin.messages().formatPlain("prestige-subtitle", placeholders), 5, 45, 15);
            }
            if (plugin.config().snapshot().progression().soundNotifications()) {
                player.playSound(player.getLocation(), plugin.config().snapshot().progression().prestigeSound(), 1.0f, 1.0f);
            }
        }
    }

    private static String safeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record CommandDraft(String command, boolean playerCommand, long delayTicks) {}

    private CompletableFuture<ProgressionResult> finishImmediate(UUID playerId, ProgressionResult result) {
        inFlight.remove(playerId);
        return CompletableFuture.completedFuture(result);
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Progression transactions must begin on the server thread");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    private enum Comparison { BEFORE, EQUAL, AFTER }
}
