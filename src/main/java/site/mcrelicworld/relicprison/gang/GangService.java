package site.mcrelicworld.relicprison.gang;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.economy.VaultEconomyAdapter;
import site.mcrelicworld.relicprison.util.ColorUtil;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class GangService {
    private static final MathContext MATH = MathContext.DECIMAL64;
    private final RelicPrisonPlugin plugin;
    private final GangRepository repository;
    private final GangConfigRepository configs;
    private final Map<UUID, GangMember> memberships = new ConcurrentHashMap<>();
    private final Map<UUID, Gang> gangs = new ConcurrentHashMap<>();
    private final Map<UUID, GangRank> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, GangStatistics> statistics = new ConcurrentHashMap<>();
    private final Map<UUID, GangMemberStatistics> memberStatistics = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Integer>> leaderboardPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> upgrades = new ConcurrentHashMap<>();
    private final Map<UUID, List<GangBooster>> boosters = new ConcurrentHashMap<>();
    private int refreshTask = -1;

    public GangService(RelicPrisonPlugin plugin, GangRepository repository, GangConfigRepository configs) {
        this.plugin = plugin;
        this.repository = repository;
        this.configs = configs;
    }

    public CompletableFuture<Void> initializeAsync() {
        plugin.database().addReconnectListener(this::refreshAll);
        return reloadBoosters().thenCompose(ignored -> refreshPositions())
                .thenCompose(ignored -> recoverMissionRewards())
                .thenCompose(ignored -> recoverSeasonRewards())
                .thenRun(() -> Bukkit.getScheduler().runTask(plugin, this::startRefreshTask));
    }

    public void shutdown() {
        if (refreshTask != -1) Bukkit.getScheduler().cancelTask(refreshTask);
    }

    private void startRefreshTask() {
        if (refreshTask != -1) Bukkit.getScheduler().cancelTask(refreshTask);
        refreshTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            reloadBoosters();
            refreshPositions();
            for (Player player : Bukkit.getOnlinePlayers()) refresh(player.getUniqueId());
        }, 40L, 200L);
    }

    public void refreshAll() {
        memberships.clear();
        gangs.clear();
        ranks.clear();
        statistics.clear();
        memberStatistics.clear();
        leaderboardPositions.clear();
        upgrades.clear();
        reloadBoosters();
        recoverMissionRewards();
        recoverSeasonRewards();
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers()
                .forEach(player -> refresh(player.getUniqueId())));
    }

    public CompletableFuture<Void> refresh(UUID playerId) {
        return repository.member(playerId).thenCompose(member -> {
            if (member.isEmpty()) {
                memberships.remove(playerId);
                return CompletableFuture.completedFuture(null);
            }
            GangMember value = member.get();
            memberships.put(playerId, value);
            return CompletableFuture.allOf(
                    repository.findById(value.gangId()).thenAccept(gang -> gang.ifPresent(found -> gangs.put(found.id(), found))),
                    repository.rank(value.rankId()).thenAccept(rank -> rank.ifPresent(found -> ranks.put(found.id(), found))),
                    repository.upgrades(value.gangId()).thenAccept(found -> upgrades.put(value.gangId(), found)),
                    repository.statistics(value.gangId()).thenAccept(found -> statistics.put(value.gangId(), found)),
                    repository.memberStatistics(value.gangId()).thenAccept(found -> found.stream()
                            .filter(stats -> stats.playerId().equals(playerId)).findFirst()
                            .ifPresent(stats -> memberStatistics.put(playerId, stats))));
        });
    }

    public Optional<GangMember> cachedMembership(UUID playerId) { return Optional.ofNullable(memberships.get(playerId)); }
    public Optional<Gang> cachedGang(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? Optional.empty() : Optional.ofNullable(gangs.get(member.gangId()));
    }
    public Optional<GangRank> cachedRank(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? Optional.empty() : Optional.ofNullable(ranks.get(member.rankId()));
    }
    public Optional<GangStatistics> cachedStatistics(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? Optional.empty() : Optional.ofNullable(statistics.get(member.gangId()));
    }
    public Optional<GangMemberStatistics> cachedMemberStatistics(UUID playerId) {
        return Optional.ofNullable(memberStatistics.get(playerId));
    }
    public int cachedOnlineMembers(UUID playerId) {
        GangMember member = memberships.get(playerId);
        if (member == null) return 0;
        return (int) memberships.entrySet().stream().filter(entry -> entry.getValue().gangId().equals(member.gangId()))
                .filter(entry -> Bukkit.getPlayer(entry.getKey()) != null).count();
    }
    public Optional<Integer> cachedLeaderboardPosition(UUID playerId, String category) {
        GangMember member = memberships.get(playerId);
        if (member == null) return Optional.empty();
        return Optional.ofNullable(leaderboardPositions.getOrDefault(category, Map.of()).get(member.gangId()));
    }
    public int cachedUpgradeTier(UUID playerId, String upgradeId) {
        GangMember member = memberships.get(playerId);
        if (member == null) return 0;
        return upgrades.getOrDefault(member.gangId(), Map.of()).getOrDefault(upgradeId, 0);
    }
    public Map<String, Integer> cachedUpgrades(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? Map.of() : upgrades.getOrDefault(member.gangId(), Map.of());
    }
    public BigDecimal effectiveBankCapacity(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? configs.config().bankCapacity()
                : effect(member.gangId(), "bank_capacity", configs.config().bankCapacity());
    }
    public Map<String, Integer> cachedLeaderboardPositions(UUID playerId) {
        GangMember member = memberships.get(playerId);
        if (member == null) return Map.of();
        Map<String, Integer> result = new java.util.HashMap<>();
        leaderboardPositions.forEach((category, positions) -> {
            Integer position = positions.get(member.gangId());
            if (position != null) result.put(category, position);
        });
        return Map.copyOf(result);
    }

    public boolean hasPermission(UUID playerId, GangPermission permission) {
        return cachedRank(playerId).map(rank -> rank.owner() || rank.permissions().contains(permission)).orElse(false);
    }

    public CompletableFuture<GangOperationResult> create(Player player, String name, String tag) {
        GangConfig config = configs.config();
        if (!config.enabled()) return CompletableFuture.completedFuture(
                GangOperationResult.failure("Gangs are currently disabled by the server"));
        String failure = validateIdentity(name, tag, config);
        if (!failure.isBlank()) return CompletableFuture.completedFuture(GangOperationResult.failure(failure));
        if (!player.hasPermission("relicprison.gang.create")) {
            return CompletableFuture.completedFuture(GangOperationResult.failure(
                    "You do not have permission to create a gang"));
        }
        BigDecimal cost = config.creationCost();
        VaultEconomyAdapter.Transaction withdrawal = cost.signum() == 0
                ? new VaultEconomyAdapter.Transaction(true, "") : plugin.economy().withdraw(player, cost);
        if (!withdrawal.success()) return CompletableFuture.completedFuture(GangOperationResult.failure(withdrawal.error()));
        int memberLimit = permissionMemberLimit(player, config);
        return repository.create(player.getUniqueId(), name.trim(), tag.trim(), memberLimit,
                config.defaultRanks(), System.currentTimeMillis()).handle((gang, error) -> {
                    if (error == null) {
                        refresh(player.getUniqueId());
                        return CompletableFuture.completedFuture(GangOperationResult.success("Gang &f" + gang.name()
                                + " &awas created successfully"));
                    }
                    if (cost.signum() == 0) return CompletableFuture.completedFuture(
                            GangOperationResult.failure(rootMessage(error)));
                    return onMainThread(() -> plugin.economy().deposit(player, cost)).thenApply(refund ->
                            GangOperationResult.failure(rootMessage(error) + (refund.success() ? "" : "; refund failed")));
                }).thenCompose(value -> value);
    }

    public CompletableFuture<GangOperationResult> invite(UUID actorId, UUID targetId) {
        return require(actorId, GangPermission.INVITE).thenCompose(context -> repository.invite(context.gang().id(),
                targetId, actorId, System.currentTimeMillis() + configs.config().inviteTimeout().toMillis(),
                System.currentTimeMillis()).thenApply(invite -> GangOperationResult.success(
                        "Gang invitation sent successfully")))
                .exceptionally(GangService::failure);
    }

    public CompletableFuture<List<GangInvite>> invites(UUID playerId) {
        return repository.invites(playerId, System.currentTimeMillis());
    }

    public CompletableFuture<GangOperationResult> respondInvite(UUID playerId, UUID inviteId, boolean accept) {
        return repository.respondInvite(inviteId, playerId, accept, System.currentTimeMillis()).thenApply(result -> {
            if (result.success() && accept) refresh(playerId);
            return result;
        });
    }

    public CompletableFuture<GangOperationResult> joinOpen(UUID playerId, String gangName) {
        return repository.findByNameOrTag(gangName).thenCompose(gang -> {
            if (gang.isEmpty()) return CompletableFuture.completedFuture(GangOperationResult.failure(
                    "No gang was found matching &f" + gangName + "&c"));
            return repository.joinOpen(gang.get().id(), playerId, System.currentTimeMillis());
        }).thenApply(result -> {
            if (result.success()) refresh(playerId);
            return result;
        });
    }

    public CompletableFuture<GangOperationResult> leave(UUID playerId) {
        GangMember member = memberships.get(playerId);
        if (member == null) return CompletableFuture.completedFuture(GangOperationResult.failure(
                "You are not currently in a gang"));
        return repository.removeMember(member.gangId(), playerId, playerId, true, System.currentTimeMillis())
                .thenApply(result -> { if (result.success()) memberships.remove(playerId); return result; });
    }

    public CompletableFuture<GangOperationResult> kick(UUID actorId, UUID targetId) {
        return require(actorId, GangPermission.KICK).thenCompose(context -> hierarchy(context, targetId, false)
                .thenCompose(target -> repository.removeMember(context.gang().id(), targetId, actorId, false,
                        System.currentTimeMillis()))).thenApply(result -> {
                    if (result.success()) memberships.remove(targetId);
                    return result;
                }).exceptionally(GangService::failure);
    }

    public CompletableFuture<GangOperationResult> changeRank(UUID actorId, UUID targetId, boolean promote) {
        GangPermission permission = promote ? GangPermission.PROMOTE : GangPermission.DEMOTE;
        return require(actorId, permission).thenCompose(context -> hierarchy(context, targetId, false)
                .thenCompose(target -> repository.ranks(context.gang().id()).thenCompose(allRanks -> {
                    List<GangRank> ordered = allRanks.stream().filter(rank -> !rank.owner())
                            .sorted(Comparator.comparingInt(GangRank::priority)).toList();
                    int index = indexOf(ordered, target.rankId());
                    int next = promote ? index + 1 : index - 1;
                    if (index < 0 || next < 0 || next >= ordered.size()) {
                        return CompletableFuture.completedFuture(GangOperationResult.failure(
                                "That member has no eligible rank change"));
                    }
                    GangRank desired = ordered.get(next);
                    if (desired.priority() >= context.rank().priority()) {
                        return CompletableFuture.completedFuture(GangOperationResult.failure(
                                "You cannot assign a rank equal to or higher than your own"));
                    }
                    return repository.setMemberRank(context.gang().id(), targetId, desired.id(), actorId,
                            System.currentTimeMillis());
                }))).thenApply(result -> { if (result.success()) refresh(targetId); return result; })
                .exceptionally(GangService::failure);
    }

    public CompletableFuture<GangOperationResult> transfer(UUID ownerId, UUID newOwner) {
        return require(ownerId, GangPermission.TRANSFER_OWNERSHIP).thenCompose(context ->
                repository.transferOwnership(context.gang().id(), ownerId, newOwner, System.currentTimeMillis()))
                .thenApply(result -> { if (result.success()) { refresh(ownerId); refresh(newOwner); } return result; })
                .exceptionally(GangService::failure);
    }

    public CompletableFuture<GangOperationResult> disband(UUID actorId) {
        return require(actorId, GangPermission.DISBAND).thenCompose(context ->
                repository.disband(context.gang().id(), actorId, System.currentTimeMillis()))
                .thenApply(result -> { if (result.success()) refreshAll(); return result; })
                .exceptionally(GangService::failure);
    }

    public CompletableFuture<GangOperationResult> deposit(Player player, BigDecimal amount, String reason) {
        if (amount.signum() <= 0) return CompletableFuture.completedFuture(GangOperationResult.failure(
                "The deposit amount must be greater than zero"));
        GangMember member = memberships.get(player.getUniqueId());
        if (member == null || !hasPermission(player.getUniqueId(), GangPermission.DEPOSIT_BANK)) {
            return CompletableFuture.completedFuture(GangOperationResult.failure(
                    "You do not have permission to deposit into the gang bank"));
        }
        VaultEconomyAdapter.Transaction withdrawal = plugin.economy().withdraw(player, amount);
        if (!withdrawal.success()) return CompletableFuture.completedFuture(GangOperationResult.failure(withdrawal.error()));
        String operationKey = "gang-deposit:" + UUID.randomUUID();
        BigDecimal capacity = effect(member.gangId(), "bank_capacity", configs.config().bankCapacity());
        return repository.changeBank(member.gangId(), player.getUniqueId(), amount,
                GangBankTransaction.Type.DEPOSIT, reason, operationKey, capacity, BigDecimal.ZERO,
                dailyKey(), System.currentTimeMillis()).handle((transaction, error) -> {
                    if (error == null) {
                        refresh(player.getUniqueId());
                        return CompletableFuture.completedFuture(GangOperationResult.success("Deposited &6"
                                + plugin.numbers().currency(amount) + " &ainto the gang bank"));
                    }
                    return onMainThread(() -> plugin.economy().deposit(player, amount)).thenApply(refund ->
                            GangOperationResult.failure(rootMessage(error) + (refund.success() ? "" : "; refund failed")));
                }).thenCompose(value -> value);
    }

    public CompletableFuture<GangOperationResult> withdraw(Player player, BigDecimal amount, String reason) {
        if (amount.signum() <= 0) return CompletableFuture.completedFuture(GangOperationResult.failure(
                "The withdrawal amount must be greater than zero"));
        GangMember member = memberships.get(player.getUniqueId());
        if (member == null || !hasPermission(player.getUniqueId(), GangPermission.WITHDRAW_BANK)) {
            return CompletableFuture.completedFuture(GangOperationResult.failure(
                    "You do not have permission to withdraw from the gang bank"));
        }
        String operationKey = "gang-withdraw:" + UUID.randomUUID();
        BigDecimal daily = configs.config().dailyWithdrawalLimit();
        return repository.changeBank(member.gangId(), player.getUniqueId(), amount.negate(),
                GangBankTransaction.Type.WITHDRAWAL, reason, operationKey, BigDecimal.ZERO, daily,
                dailyKey(), System.currentTimeMillis()).thenCompose(transaction ->
                    onMainThread(() -> plugin.economy().deposit(player, amount)).thenCompose(economy -> {
                        if (economy.success()) {
                            refresh(player.getUniqueId());
                            return CompletableFuture.completedFuture(GangOperationResult.success("Withdrew &6"
                                    + plugin.numbers().currency(amount) + " &afrom the gang bank"));
                        }
                        return repository.changeBank(member.gangId(), player.getUniqueId(), amount,
                                GangBankTransaction.Type.ADMIN_ADJUSTMENT, "Vault compensation: " + economy.error(),
                                operationKey + ":compensate", BigDecimal.ZERO, BigDecimal.ZERO, dailyKey(),
                                System.currentTimeMillis()).handle((ignored, compensationError) -> GangOperationResult.failure(
                                economy.error() + (compensationError == null ? "; bank restored" : "; bank recovery required")));
                    })).exceptionally(GangService::failure);
    }

    public CompletableFuture<GangOperationResult> purchaseUpgrade(UUID actorId, String upgradeId) {
        GangConfig.UpgradeDefinition definition = configs.config().upgrades().get(
                GangConfigRepository.normalizeId(upgradeId));
        if (definition == null) return CompletableFuture.completedFuture(GangOperationResult.failure(
                "That gang upgrade does not exist"));
        return require(actorId, GangPermission.PURCHASE_UPGRADES).thenCompose(context -> repository.purchaseUpgrade(
                context.gang().id(), actorId, definition, "gang-upgrade:" + UUID.randomUUID(),
                configs.config().maximumMemberLimit(), System.currentTimeMillis()).thenApply(state -> GangOperationResult.success(
                definition.displayName() + " upgraded to tier " + state.tier())))
                .thenApply(result -> { if (result.success()) refresh(actorId); return result; })
                .exceptionally(GangService::failure);
    }

    public CompletableFuture<GangRank> createRank(UUID actorId, String displayName, int priority, String color,
                                                   Set<GangPermission> permissions) {
        return require(actorId, GangPermission.MANAGE_RANKS).thenCompose(context -> {
            if (priority >= context.rank().priority()) return CompletableFuture.failedFuture(
                    new GangRepository.Conflict("Custom rank must be below the actor's rank"));
            return repository.createRank(context.gang().id(), actorId, displayName, priority, color, permissions,
                    System.currentTimeMillis());
        });
    }

    public CompletableFuture<GangOperationResult> setRankPermission(UUID actorId, UUID rankId,
                                                                     GangPermission permission, boolean enabled) {
        return require(actorId, GangPermission.MANAGE_RANK_PERMISSIONS).thenCompose(context ->
                repository.rank(rankId).thenCompose(rank -> {
                    if (rank.isEmpty() || !rank.get().gangId().equals(context.gang().id())) {
                        return CompletableFuture.completedFuture(GangOperationResult.failure("That gang rank was not found"));
                    }
                    if (rank.get().priority() >= context.rank().priority() && !context.rank().owner()) {
                        return CompletableFuture.completedFuture(GangOperationResult.failure(
                                "You cannot edit a rank equal to or higher than your own"));
                    }
                    return repository.setRankPermission(context.gang().id(), rankId, actorId, permission, enabled,
                            System.currentTimeMillis());
                })).exceptionally(GangService::failure);
    }

    public CompletableFuture<GangOperationResult> editRank(UUID actorId, UUID rankId, String displayName,
                                                            int priority, String color) {
        return require(actorId, GangPermission.MANAGE_RANKS).thenCompose(context -> {
            if (priority >= context.rank().priority()) {
                return CompletableFuture.completedFuture(GangOperationResult.failure(
                        "Rank priority must be below your rank"));
            }
            return repository.editRank(context.gang().id(), rankId, actorId, displayName, priority, color,
                    System.currentTimeMillis());
        }).exceptionally(GangService::failure);
    }

    public CompletableFuture<GangOperationResult> deleteRank(UUID actorId, UUID rankId, UUID fallbackRankId) {
        return require(actorId, GangPermission.MANAGE_RANKS).thenCompose(context -> repository.deleteRank(
                context.gang().id(), rankId, fallbackRankId, actorId, System.currentTimeMillis()))
                .exceptionally(GangService::failure);
    }

    public CompletableFuture<GangOperationResult> edit(UUID actorId, GangRepository.Edit edit) {
        GangPermission permission = edit.field() == GangRepository.EditField.JOIN_MODE
                ? GangPermission.EDIT_PRIVACY : GangPermission.EDIT_IDENTITY;
        return require(actorId, permission).thenCompose(context -> {
            if (edit.field() == GangRepository.EditField.NAME) {
                String failure = validateIdentity(edit.value(), context.gang().tag(), configs.config());
                if (!failure.isBlank()) return CompletableFuture.completedFuture(GangOperationResult.failure(failure));
            }
            if (edit.field() == GangRepository.EditField.TAG) {
                String failure = validateIdentity(context.gang().name(), edit.value(), configs.config());
                if (!failure.isBlank()) return CompletableFuture.completedFuture(GangOperationResult.failure(failure));
            }
            if (edit.field() == GangRepository.EditField.JOIN_MODE) {
                try { GangJoinMode.valueOf(edit.value().toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException error) {
                    return CompletableFuture.completedFuture(GangOperationResult.failure(
                            "Invalid join mode; use &fOPEN&c, &fINVITE_ONLY&c, or &fCLOSED&c"));
                }
            }
            return repository.editGang(context.gang().id(), actorId, edit, System.currentTimeMillis());
        }).thenApply(result -> { if (result.success()) refresh(actorId); return result; })
                .exceptionally(GangService::failure);
    }

    public CompletableFuture<GangOperationResult> setHome(Player player) {
        return require(player.getUniqueId(), GangPermission.SET_GANG_HOME).thenCompose(context -> {
            org.bukkit.Location location = player.getLocation();
            Gang.GangHome home = new Gang.GangHome(location.getWorld().getName(), location.getX(), location.getY(),
                    location.getZ(), location.getYaw(), location.getPitch());
            return repository.setHome(context.gang().id(), player.getUniqueId(), home, System.currentTimeMillis());
        }).thenApply(result -> { if (result.success()) refresh(player.getUniqueId()); return result; })
                .exceptionally(GangService::failure);
    }

    public GangOperationResult useHome(Player player) {
        if (!hasPermission(player.getUniqueId(), GangPermission.USE_GANG_HOME)) {
            return GangOperationResult.failure("Your gang rank does not have permission to use the gang home");
        }
        Gang gang = cachedGang(player.getUniqueId()).orElse(null);
        if (gang == null || gang.home() == null) return GangOperationResult.failure(
                "Your gang does not have a home set.\n&7Use &e/gang sethome &7to create one.");
        if (plugin.combatTags() != null && plugin.combatTags().blocksMineTeleport(player)) {
            return GangOperationResult.failure("You cannot teleport to the gang home while combat tagged");
        }
        org.bukkit.World world = Bukkit.getWorld(gang.home().world());
        if (world == null) return GangOperationResult.failure("The gang home world is currently unavailable");
        player.teleportAsync(new org.bukkit.Location(world, gang.home().x(), gang.home().y(), gang.home().z(),
                gang.home().yaw(), gang.home().pitch()));
        return GangOperationResult.success("Teleporting to your gang home");
    }

    public CompletableFuture<GangRepository.ContributionResult> recordContribution(UUID playerId,
            GangConfig.ContributionType type, BigDecimal amount, String targetKey, String operationKey) {
        if (!configs.config().enabled() || amount == null || amount.signum() <= 0) {
            return CompletableFuture.completedFuture(new GangRepository.ContributionResult(false, false, null,
                    0, 0, BigDecimal.ZERO, List.of()));
        }
        GangConfig config = configs.config();
        BigDecimal source = config.xpSources().getOrDefault(type, BigDecimal.ZERO);
        BigDecimal eventBonus = type == GangConfig.ContributionType.BLOCK_EVENTS
                ? cachedGang(playerId).map(gang -> effect(gang.id(), "block_event_multiplier", BigDecimal.ONE))
                        .orElse(BigDecimal.ONE)
                : BigDecimal.ONE;
        BigDecimal xp = amount.multiply(source, MATH).multiply(eventBonus, MATH)
                .multiply(multiplier(playerId, GangBooster.Type.GANG_XP), MATH);
        return repository.recordContribution(playerId, type, amount, xp, targetKey == null ? "" : targetKey,
                operationKey, config, periodKey(type), System.currentTimeMillis()).whenComplete((result, error) -> {
                    if (error == null && result.applied()) {
                        refresh(playerId);
                        if (result.newLevel() > result.oldLevel()) notifyGang(result.gangId(),
                                "&aGang leveled up to &f" + result.newLevel() + "&a!");
                        for (String mission : result.completedMissions()) notifyGang(result.gangId(),
                                "&eGang mission completed: &f" + mission + "&e. Claim it in /gang missions.");
                    }
                });
    }

    public BigDecimal multiplier(UUID playerId, GangBooster.Type type) {
        if (!configs.config().enabled()) return BigDecimal.ONE;
        GangMember member = memberships.get(playerId);
        if (member == null) return BigDecimal.ONE;
        String effect = switch (type) {
            case SELL -> "sell_multiplier";
            case MINING_XP -> "mining_xp_multiplier";
            case GANG_XP -> "gang_xp_multiplier";
        };
        BigDecimal upgrade = effect(member.gangId(), effect, BigDecimal.ONE);
        BigDecimal active = boosters.getOrDefault(member.gangId(), List.of()).stream()
                .filter(value -> value.type() == type && value.active(System.currentTimeMillis()))
                .map(GangBooster::multiplier).max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        return upgrade.multiply(active, MATH).max(BigDecimal.ONE);
    }

    public CompletableFuture<GangBooster> activateBooster(UUID actorId, GangBooster.Type type,
                                                           BigDecimal multiplier, long durationMillis,
                                                           long delayMillis, String source) {
        return require(actorId, GangPermission.MANAGE_GANG_BOOSTERS).thenCompose(context -> {
            if (multiplier.compareTo(BigDecimal.ONE) < 0) return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Booster multiplier must be at least 1"));
            if (multiplier.compareTo(configs.config().maximumBoosterMultiplier()) > 0
                    || durationMillis <= 0 || durationMillis > configs.config().maximumBoosterDuration().toMillis()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Booster exceeds configured limits"));
            }
            long duration = BigDecimal.valueOf(durationMillis)
                    .multiply(effect(context.gang().id(), "booster_duration_multiplier", BigDecimal.ONE))
                    .longValue();
            long starts = System.currentTimeMillis() + Math.max(0L, delayMillis);
            return repository.activateBooster(context.gang().id(), type, multiplier, starts, starts + duration,
                    actorId, source, System.currentTimeMillis());
        }).whenComplete((booster, error) -> { if (error == null) reloadBoosters(); });
    }

    public void tickBoosters() {
        repository.expireBoosters(System.currentTimeMillis()).thenCompose(ignored -> reloadBoosters())
                .exceptionally(error -> null);
    }

    public CompletableFuture<List<GangMissionState>> missions(UUID playerId) {
        GangMember member = memberships.get(playerId);
        if (member == null) return CompletableFuture.completedFuture(List.of());
        return repository.missions(member.gangId(), configs.config(), periodKey(GangConfig.ContributionType.MISSIONS),
                System.currentTimeMillis());
    }

    public CompletableFuture<GangOperationResult> claimMission(UUID actorId, String missionId) {
        GangMember member = memberships.get(actorId);
        if (member == null) return CompletableFuture.completedFuture(GangOperationResult.failure(
                "You are not currently in a gang"));
        String normalized = GangConfigRepository.normalizeId(missionId);
        GangConfig.MissionDefinition definition = configs.config().missions().get(normalized);
        if (definition == null) return CompletableFuture.completedFuture(GangOperationResult.failure(
                "That gang mission does not exist"));
        String period = periodKey(GangConfig.ContributionType.MISSIONS);
        return repository.claimMission(member.gangId(), normalized, period, actorId, configs.config(),
                System.currentTimeMillis()).thenCompose(result -> {
                    if (!result.success()) return CompletableFuture.completedFuture(result);
                    refresh(actorId);
                    reloadBoosters();
                    return recoverMissionRewards().thenCompose(ignored -> plugin.rewardLedger().deliverDueAsync())
                            .thenApply(ignored -> result);
                });
    }

    public void sendChat(Player sender, String message) {
        Gang gang = cachedGang(sender.getUniqueId()).orElse(null);
        GangRank rank = cachedRank(sender.getUniqueId()).orElse(null);
        if (gang == null || rank == null) {
            plugin.messages().gangError(sender, "You are not currently in a gang.");
            return;
        }
        String formatted = color("&8[&r" + gang.color() + gang.tag() + "&8] " + rank.color()
                + rank.displayName() + " &f" + sender.getName() + "&7: &f" + message);
        memberships.forEach((playerId, membership) -> {
            if (!membership.gangId().equals(gang.id())) return;
            Player recipient = Bukkit.getPlayer(playerId);
            if (recipient != null) recipient.sendMessage(formatted);
        });
    }

    public CompletableFuture<GangOperationResult> toggleChat(UUID playerId) {
        GangMember member = memberships.get(playerId);
        if (member == null) return CompletableFuture.completedFuture(GangOperationResult.failure(
                "You are not currently in a gang"));
        boolean enabled = !member.chatEnabled();
        return repository.setChatToggle(playerId, enabled).thenApply(result -> {
            if (result.success()) memberships.put(playerId, new GangMember(member.playerId(), member.gangId(),
                    member.rankId(), member.joinedAt(), member.lastSeenAt(), enabled, member.version() + 1));
            return new GangOperationResult(result.success(), result.success()
                    ? "Gang chat is now &f" + (enabled ? "enabled" : "disabled") + "&a" : result.message());
        });
    }

    public GangRepository repository() { return repository; }
    public GangConfig config() { return configs.config(); }

    public CompletableFuture<List<GangRank>> ranks(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? CompletableFuture.completedFuture(List.of()) : repository.ranks(member.gangId());
    }

    public CompletableFuture<List<GangMember>> members(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? CompletableFuture.completedFuture(List.of()) : repository.members(member.gangId());
    }

    public CompletableFuture<GangStatistics> statistics(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? CompletableFuture.completedFuture(null) : repository.statistics(member.gangId());
    }

    public CompletableFuture<List<GangMemberStatistics>> contributions(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? CompletableFuture.completedFuture(List.of())
                : repository.memberStatistics(member.gangId());
    }

    public CompletableFuture<List<GangLeaderboardEntry>> leaderboard(String category, int limit) {
        return repository.leaderboard(category, limit);
    }

    public List<GangBooster> boosters(UUID playerId) {
        GangMember member = memberships.get(playerId);
        return member == null ? List.of() : boosters.getOrDefault(member.gangId(), List.of());
    }

    public CompletableFuture<Optional<Integer>> leaderboardPosition(UUID playerId, String category) {
        GangMember member = memberships.get(playerId);
        if (member == null) return CompletableFuture.completedFuture(Optional.empty());
        return repository.leaderboard(category, 1000).thenApply(entries -> entries.stream()
                .filter(entry -> entry.gangId().equals(member.gangId())).map(GangLeaderboardEntry::position).findFirst());
    }

    private CompletableFuture<Context> require(UUID playerId, GangPermission permission) {
        GangMember member = memberships.get(playerId);
        Gang gang = member == null ? null : gangs.get(member.gangId());
        GangRank rank = member == null ? null : ranks.get(member.rankId());
        if (member == null || gang == null || rank == null) {
            return refresh(playerId).thenCompose(ignored -> requireCached(playerId, permission));
        }
        return requireCached(playerId, permission);
    }

    private CompletableFuture<Context> requireCached(UUID playerId, GangPermission permission) {
        GangMember member = memberships.get(playerId);
        Gang gang = member == null ? null : gangs.get(member.gangId());
        GangRank rank = member == null ? null : ranks.get(member.rankId());
        if (member == null || gang == null || rank == null) {
            return CompletableFuture.failedFuture(new GangRepository.Conflict("Player is not in a gang"));
        }
        if (!rank.owner() && !rank.permissions().contains(permission)) {
            return CompletableFuture.failedFuture(new GangRepository.Conflict("Gang rank lacks " + permission.key()));
        }
        return CompletableFuture.completedFuture(new Context(gang, member, rank));
    }

    private CompletableFuture<GangMember> hierarchy(Context actor, UUID targetId, boolean allowSelf) {
        if (!allowSelf && actor.member().playerId().equals(targetId)) {
            return CompletableFuture.failedFuture(new GangRepository.Conflict("Cannot target yourself"));
        }
        return repository.member(targetId).thenCompose(target -> {
            if (target.isEmpty() || !target.get().gangId().equals(actor.gang().id())) {
                return CompletableFuture.failedFuture(new GangRepository.Conflict("Target is not in your gang"));
            }
            return repository.rank(target.get().rankId()).thenCompose(targetRank -> {
                if (targetRank.isEmpty() || targetRank.get().priority() >= actor.rank().priority()) {
                    return CompletableFuture.failedFuture(new GangRepository.Conflict("Cannot manage an equal or higher rank"));
                }
                return CompletableFuture.completedFuture(target.get());
            });
        });
    }

    private CompletableFuture<Void> reloadBoosters() {
        long now = System.currentTimeMillis();
        return repository.expireBoosters(now).thenCompose(ignored -> repository.activeBoosters(now)).thenAccept(active -> {
            Map<UUID, List<GangBooster>> next = new java.util.HashMap<>();
            for (GangBooster booster : active) next.computeIfAbsent(booster.gangId(), ignored -> new ArrayList<>()).add(booster);
            boosters.clear();
            next.forEach((gangId, values) -> boosters.put(gangId, List.copyOf(values)));
            if (plugin.multiplierService() != null) plugin.multiplierService().invalidateAll();
        });
    }

    private CompletableFuture<Void> refreshPositions() {
        List<CompletableFuture<Void>> loads = new ArrayList<>();
        for (String category : configs.config().leaderboardCategories()) {
            loads.add(repository.leaderboard(category, 1000).thenAccept(entries -> {
                Map<UUID, Integer> positions = new java.util.HashMap<>();
                entries.forEach(entry -> positions.put(entry.gangId(), entry.position()));
                leaderboardPositions.put(category, Map.copyOf(positions));
            }));
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    private BigDecimal effect(UUID gangId, String effectId, BigDecimal fallback) {
        Map<String, Integer> states = upgrades.getOrDefault(gangId, Map.of());
        BigDecimal result = fallback;
        for (Map.Entry<String, Integer> state : states.entrySet()) {
            GangConfig.UpgradeDefinition definition = configs.config().upgrades().get(state.getKey());
            if (definition == null || state.getValue() <= 0 || state.getValue() > definition.maxTier()) continue;
            BigDecimal configured = definition.tier(state.getValue()).effects().get(effectId);
            if (configured != null) result = configured;
        }
        return result;
    }

    private int permissionMemberLimit(Player player, GangConfig config) {
        int limit = config.defaultMemberLimit();
        for (var entry : config.memberPermissionLimits().entrySet()) {
            if (player.hasPermission(entry.getKey())) limit = Math.max(limit, entry.getValue());
        }
        return Math.min(limit, config.maximumMemberLimit());
    }

    private String validateIdentity(String name, String tag, GangConfig config) {
        if (name == null || name.length() < config.nameMinimum() || name.length() > config.nameMaximum()
                || !Pattern.matches(config.namePattern(), name)) return "Invalid gang name";
        if (tag == null || tag.length() < config.tagMinimum() || tag.length() > config.tagMaximum()
                || !Pattern.matches(config.tagPattern(), tag)) return "Invalid gang tag";
        return "";
    }

    private String dailyKey() {
        return LocalDate.now(plugin.config().snapshot().timezone()).toString();
    }

    private String periodKey(GangConfig.ContributionType ignored) {
        long now = System.currentTimeMillis();
        ZoneId zone = plugin.config().snapshot().timezone();
        LocalDate date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return date + "|" + date.get(WeekFields.ISO.weekBasedYear()) + "-W"
                + String.format("%02d", date.get(WeekFields.ISO.weekOfWeekBasedYear()))
                + "|" + date.getYear() + '-' + String.format("%02d", date.getMonthValue());
    }

    public CompletableFuture<Void> recoverMissionRewards() {
        return repository.missionRewardClaims(1000).thenCompose(claims -> CompletableFuture.allOf(claims.stream()
                .map(claim -> {
                    String[] payload = claim.payload().split("\\|", 2);
                    if (payload.length != 2 || payload[0].isBlank()) return CompletableFuture.completedFuture(false);
                    UUID playerId = UUID.fromString(payload[0]);
                    List<String> definitions = payload[1].isBlank() ? List.of() : List.of(payload[1].split(";"));
                    return plugin.rewardLedger().createPackage(claim.operationKey(), "gang-mission",
                            claim.operationKey(), playerId, payload[1], GangRewardComponents.parse(definitions, "mission"));
                }).toArray(CompletableFuture[]::new)));
    }

    public CompletableFuture<Void> recoverSeasonRewards() {
        return repository.pendingSeasonRewards(1000).thenCompose(results -> CompletableFuture.allOf(results.stream()
                .map(result -> repository.findById(result.gangId()).thenCompose(gang -> {
                    if (gang.isEmpty()) return repository.markSeasonRewardState(result, "MISSING_GANG")
                            .thenApply(ignored -> false);
                    List<String> definitions = GangRewardComponents.forPosition(result.frozenReward(), result.position())
                            .stream().map(value -> value.replace("%gang%", result.gangName())
                                    .replace("%position%", String.valueOf(result.position()))
                                    .replace("%value%", result.value().toPlainString())).toList();
                    if (definitions.isEmpty()) return repository.markSeasonRewardState(result, "NO_REWARD")
                            .thenApply(ignored -> false);
                    String packageId = "gang-season-" + result.seasonId() + '-' + result.category() + '-'
                            + result.position();
                    return plugin.rewardLedger().createPackage(packageId, "gang-season",
                            result.seasonId() + ':' + result.category(), gang.get().ownerId(), result.frozenReward(),
                            GangRewardComponents.parse(definitions, "season"))
                            .thenCompose(created -> repository.markSeasonRewardState(result, "PACKAGE_CREATED"))
                            .thenApply(ignored -> true);
                })).toArray(CompletableFuture[]::new)));
    }

    private void notifyGang(UUID gangId, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> memberships.forEach((playerId, membership) -> {
            if (!membership.gangId().equals(gangId)) return;
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) plugin.messages().gangInfo(player, message);
        }));
    }

    private static int indexOf(List<GangRank> ranks, UUID rankId) {
        for (int index = 0; index < ranks.size(); index++) if (ranks.get(index).id().equals(rankId)) return index;
        return -1;
    }

    private static String color(String value) {
        return ColorUtil.color(value);
    }

    private <T> CompletableFuture<T> onMainThread(java.util.function.Supplier<T> action) {
        if (Bukkit.isPrimaryThread()) return CompletableFuture.completedFuture(action.get());
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { result.complete(action.get()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }

    private static GangOperationResult failure(Throwable error) {
        return GangOperationResult.failure(rootMessage(error));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Gang operation failed" : current.getMessage();
    }

    private record Context(Gang gang, GangMember member, GangRank rank) { }
}
