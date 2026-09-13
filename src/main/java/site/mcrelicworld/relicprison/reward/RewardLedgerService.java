package site.mcrelicworld.relicprison.reward;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.mining.MiningConfig;
import site.mcrelicworld.relicprison.mining.BulkRewardTarget;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RewardLedgerService {
    private static final int DUE_BATCH_LIMIT = 100;
    private static final int MAX_ATTEMPTS = 5;
    private static final long CLAIM_LEASE_MILLIS = 60_000L;
    private static final long OFFLINE_RETRY_MILLIS = 30_000L;

    private final RelicPrisonPlugin plugin;
    private final RewardLedgerRepository repository;
    private int taskId = -1;

    public RewardLedgerService(RelicPrisonPlugin plugin, RewardLedgerRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public CompletableFuture<Boolean> createPackage(String packageId, String sourceSystem, String sourceOperationId,
                                                    UUID playerId, String frozenPayload,
                                                    List<ComponentDraft> drafts) {
        long now = System.currentTimeMillis();
        RewardPackageRecord rewardPackage = new RewardPackageRecord(packageId, sourceSystem, sourceOperationId,
                playerId, RewardComponentState.PENDING, now, now, frozenPayload == null ? "" : frozenPayload, "");
        List<RewardComponentRecord> components = drafts.stream()
                .map(draft -> draft.toRecord(packageId, sourceSystem, sourceOperationId, playerId, now))
                .toList();
        return repository.createPackage(rewardPackage, components);
    }

    public void start() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        repository.recoverInterruptedComponents("Server stopped while this reward component was claimed or running")
                .thenAccept(result -> {
                    if (result.retryable() > 0) plugin.getLogger().warning(result.retryable()
                            + " replay-safe bulk reward component(s) were scheduled for recovery.");
                    if (result.ambiguous() > 0) plugin.getLogger().warning(result.ambiguous()
                            + " non-replay-safe reward component(s) require staff review after restart.");
                });
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::deliverDueAsync, 100L, 100L);
    }

    public void shutdown() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    public CompletableFuture<Void> deliverDueAsync() {
        long now = System.currentTimeMillis();
        return repository.recoverExpiredClaims(now, "claim lease expired before completion")
                .thenCompose(ignored -> repository.claimDue(now, "scheduler", CLAIM_LEASE_MILLIS, DUE_BATCH_LIMIT))
                .thenAccept(claims -> Bukkit.getScheduler().runTask(plugin,
                        () -> claims.forEach(this::deliverClaim)));
    }

    public CompletableFuture<Void> recoverAfterDatabaseReconnect() {
        return repository.recoverInterruptedComponents("Database connection was lost during reward delivery")
                .thenCompose(ignored -> deliverDueAsync());
    }

    public CompletableFuture<Void> deliverPending(UUID playerId) {
        long now = System.currentTimeMillis();
        return repository.claimPendingFor(playerId, now, "player-join:" + playerId,
                        CLAIM_LEASE_MILLIS, DUE_BATCH_LIMIT)
                .thenAccept(claims -> Bukkit.getScheduler().runTask(plugin,
                        () -> claims.forEach(this::deliverClaim)));
    }

    public CompletableFuture<List<RewardComponentRecord>> pending(int limit) {
        return repository.pending(limit);
    }

    public CompletableFuture<List<RewardComponentRecord>> failed(int limit) {
        return repository.failed(limit);
    }

    public CompletableFuture<List<RewardComponentRecord>> packageComponents(String packageId) {
        return repository.components(packageId);
    }

    public CompletableFuture<java.util.Optional<RewardPackageRecord>> packageById(String packageId) {
        return repository.packageById(packageId);
    }

    public CompletableFuture<Void> retry(String packageId, String componentId) {
        long now = System.currentTimeMillis();
        return repository.prepareRetry(packageId, componentId, "staff retry", now).thenAccept(updated -> {
            if (!updated) throw new IllegalArgumentException("Unknown or non-retryable reward component");
        });
    }

    public CompletableFuture<Void> cancel(String packageId, String componentId, String reason) {
        return repository.component(packageId, componentId).thenCompose(record -> {
            if (record.isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown reward component"));
            if (record.get().state() == RewardComponentState.DELIVERED) {
                return CompletableFuture.failedFuture(new IllegalStateException("Delivered components cannot be canceled"));
            }
            return repository.mark(packageId, componentId, RewardComponentState.CANCELED,
                    record.get().attemptCount(), reason);
        });
    }

    private void deliverClaim(RewardLedgerRepository.ClaimedComponent claim) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Reward delivery must run on the server thread");
        repository.markClaimRunning(claim).thenAccept(running -> {
            if (!running) return;
            Bukkit.getScheduler().runTask(plugin, () -> deliverNowAsync(claim.component())
                    .whenComplete((result, error) -> {
                        DeliveryResult finalResult = error == null ? result : retryOrReview(claim.component(),
                                rootMessage(error), true);
                        repository.completeClaim(claim, finalResult.state(), finalResult.failure(),
                                finalResult.nextAttemptAt());
                    }));
        });
    }

    private CompletableFuture<DeliveryResult> deliverNowAsync(RewardComponentRecord component) {
        try {
            Player player = component.playerId() == null ? null : Bukkit.getPlayer(component.playerId());
            return switch (component.componentType()) {
                case CONSOLE_COMMAND, KEY_COMMAND -> deliverConsoleCommand(component);
                case PLAYER_COMMAND -> deliverPlayerCommand(component, player);
                case ANNOUNCEMENT -> {
                    Bukkit.broadcastMessage(decode(component.payload()));
                    yield CompletableFuture.completedFuture(DeliveryResult.delivered());
                }
                case MONEY -> deliverMoney(component, player);
                case EXPERIENCE -> deliverExperience(component, player);
                case VANILLA_ITEM -> deliverVanillaItem(component, player);
                case ITEMSADDER_ITEM -> deliverItemsAdderItem(component, player);
                case PERSONAL_BOOSTER, SERVER_BOOSTER -> deliverBooster(component);
                case PERMISSION_ACTION -> deliverPermission(component);
                case STATISTIC_ADJUSTMENT -> deliverStatistic(component);
                case OTHER -> CompletableFuture.completedFuture(
                        DeliveryResult.review("OTHER reward component requires a specific delivery adapter"));
            };
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(retryOrReview(component, rootMessage(error), true));
        }
    }

    private CompletableFuture<DeliveryResult> deliverConsoleCommand(RewardComponentRecord component) {
        String command = decode(component.payload());
        boolean success = plugin.commandDispatch() == null
                ? Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                : plugin.commandDispatch().dispatchConsole("reward-ledger", command);
        return CompletableFuture.completedFuture(success ? DeliveryResult.delivered()
                : retryOrReview(component, "Command returned false", false));
    }

    private CompletableFuture<DeliveryResult> deliverPlayerCommand(RewardComponentRecord component, Player player) {
        if (player == null || !player.isOnline()) return CompletableFuture.completedFuture(scheduled());
        player.performCommand(decode(component.payload()));
        return CompletableFuture.completedFuture(DeliveryResult.delivered());
    }

    private CompletableFuture<DeliveryResult> deliverMoney(RewardComponentRecord component, Player player) {
        if (player == null || !player.isOnline()) return CompletableFuture.completedFuture(scheduled());
        BigDecimal amount = remainingMoney(component, player);
        if (amount.signum() == 0) return CompletableFuture.completedFuture(DeliveryResult.delivered());
        var transaction = amount.signum() < 0
                ? plugin.economy().withdraw(player, amount.abs())
                : plugin.economy().deposit(player, amount);
        return CompletableFuture.completedFuture(transaction.success() ? DeliveryResult.delivered()
                : retryOrReview(component, transaction.error(), false));
    }

    private CompletableFuture<DeliveryResult> deliverExperience(RewardComponentRecord component, Player player) {
        if (player == null || !player.isOnline()) return CompletableFuture.completedFuture(scheduled());
        int target = intField(component.payload(), "target", -1);
        int amount = BulkRewardTarget.remainingExperience(player.getTotalExperience(), target,
                component.amount().intValue());
        if (amount > 0) player.giveExp(amount);
        return CompletableFuture.completedFuture(DeliveryResult.delivered());
    }

    private BigDecimal remainingMoney(RewardComponentRecord component, Player player) {
        BigDecimal target = decimal(fields(component.payload()).get("target"), null);
        BigDecimal current = plugin.economy().balance(player);
        return BulkRewardTarget.remainingMoney(current, target, component.amount());
    }

    private CompletableFuture<DeliveryResult> deliverVanillaItem(RewardComponentRecord component, Player player) {
        if (player == null || !player.isOnline()) return CompletableFuture.completedFuture(scheduled());
        Material material = Material.matchMaterial(decode(component.payload()));
        if (material == null || material.isAir()) {
            return CompletableFuture.completedFuture(DeliveryResult.review("Invalid frozen vanilla item material"));
        }
        deliverItems(player, List.of(new ItemStack(material, intAmount(component))));
        return CompletableFuture.completedFuture(DeliveryResult.delivered());
    }

    private CompletableFuture<DeliveryResult> deliverItemsAdderItem(RewardComponentRecord component, Player player) {
        if (player == null || !player.isOnline()) return CompletableFuture.completedFuture(scheduled());
        if (plugin.itemsAdder() == null || !plugin.itemsAdder().connected()) {
            return CompletableFuture.completedFuture(retryOrReview(component, "ItemsAdder is unavailable", false));
        }
        return plugin.itemsAdder().item(decode(component.payload()), intAmount(component))
                .map(item -> {
                    deliverItems(player, List.of(item));
                    return CompletableFuture.completedFuture(DeliveryResult.delivered());
                })
                .orElseGet(() -> CompletableFuture.completedFuture(
                        DeliveryResult.review("Frozen ItemsAdder item is unavailable: " + decode(component.payload()))));
    }

    private CompletableFuture<DeliveryResult> deliverBooster(RewardComponentRecord component) {
        Map<String, String> payload = fields(component.payload());
        boolean serverWide = Boolean.parseBoolean(payload.getOrDefault("server", "false"));
        UUID owner = serverWide ? null : component.playerId();
        if (!serverWide && owner == null) return CompletableFuture.completedFuture(scheduled());
        BigDecimal multiplier = decimal(payload.get("multiplier"), component.amount().max(BigDecimal.ONE));
        long duration = longValue(payload.get("duration"), 0L);
        String activatedBy = payload.getOrDefault("activatedBy", "reward-ledger");
        return plugin.boosterService().activate(owner, serverWide, multiplier, duration, activatedBy)
                .thenApply(result -> result.success() ? DeliveryResult.delivered()
                        : retryOrReview(component, result.message(), false));
    }

    private CompletableFuture<DeliveryResult> deliverPermission(RewardComponentRecord component) {
        if (component.playerId() == null) {
            return CompletableFuture.completedFuture(DeliveryResult.review("Permission reward requires a player UUID"));
        }
        Map<String, String> payload = fields(component.payload());
        String node = payload.getOrDefault("node", "").trim();
        if (node.isBlank() || node.contains(" ")) {
            return CompletableFuture.completedFuture(DeliveryResult.review("Invalid frozen permission node"));
        }
        boolean grant = Boolean.parseBoolean(payload.getOrDefault("grant", "true"));
        String command = "lp user " + component.playerId() + " permission " + (grant ? "set " : "unset ") + node;
        boolean success = plugin.commandDispatch() == null
                ? Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                : plugin.commandDispatch().dispatchConsole("reward-ledger-permission", command);
        return CompletableFuture.completedFuture(success ? DeliveryResult.delivered()
                : retryOrReview(component, "Permission command returned false", false));
    }

    private CompletableFuture<DeliveryResult> deliverStatistic(RewardComponentRecord component) {
        if (component.playerId() == null || plugin.statistics() == null) {
            return CompletableFuture.completedFuture(DeliveryResult.review("Statistic reward requires player statistics"));
        }
        String statistic = fields(component.payload()).getOrDefault("statistic", "").toLowerCase(Locale.ROOT);
        long amount = Math.max(0L, Math.min(10_000L, component.amount().longValue()));
        switch (statistic) {
            case "items_sold" -> plugin.statistics().recordItemsSold(component.playerId(), amount);
            case "rankups" -> repeat(amount, () -> plugin.statistics().recordRankup(component.playerId()));
            case "prestiges" -> repeat(amount, () -> plugin.statistics().recordPrestige(component.playerId()));
            case "boosters" -> repeat(amount, () -> plugin.statistics().recordBooster(component.playerId()));
            default -> {
                return CompletableFuture.completedFuture(
                        DeliveryResult.review("Unsupported frozen statistic adjustment: " + statistic));
            }
        }
        return CompletableFuture.completedFuture(DeliveryResult.delivered());
    }

    private void deliverItems(Player player, List<ItemStack> items) {
        if (items.isEmpty()) return;
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(items.toArray(ItemStack[]::new));
        if (overflow.isEmpty()) return;
        MiningConfig.OverflowMode mode = plugin.miningConfigs().config().overflowMode();
        if (mode == MiningConfig.OverflowMode.CANCEL) {
            throw new IllegalStateException("Inventory is full and overflow mode is CANCEL");
        }
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private static DeliveryResult retryOrReview(RewardComponentRecord component, String failure, boolean ambiguous) {
        if (ambiguous) return DeliveryResult.ambiguous(failure);
        return component.attemptCount() >= MAX_ATTEMPTS
                ? DeliveryResult.review(failure) : DeliveryResult.retry(failure);
    }

    private static DeliveryResult scheduled() {
        return new DeliveryResult(RewardComponentState.SCHEDULED, "player offline",
                System.currentTimeMillis() + OFFLINE_RETRY_MILLIS);
    }

    public record ComponentDraft(String componentId, RewardComponentType type, String payload,
                                 BigDecimal amount, long dueAt, String idempotencyKey) {
        public static ComponentDraft consoleCommand(String componentId, String command, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.CONSOLE_COMMAND, encode(command),
                    BigDecimal.ZERO, dueAt, componentId);
        }

        public static ComponentDraft keyCommand(String componentId, String command, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.KEY_COMMAND, encode(command),
                    BigDecimal.ZERO, dueAt, componentId);
        }

        public static ComponentDraft playerCommand(String componentId, String command, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.PLAYER_COMMAND, encode(command),
                    BigDecimal.ZERO, dueAt, componentId);
        }

        public static ComponentDraft announcement(String componentId, String message, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.ANNOUNCEMENT, encode(message),
                    BigDecimal.ZERO, dueAt, componentId);
        }

        public static ComponentDraft money(String componentId, BigDecimal amount, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.MONEY, "",
                    amount == null ? BigDecimal.ZERO : amount, dueAt, componentId);
        }

        public static ComponentDraft moneyTarget(String componentId, BigDecimal amount, BigDecimal target,
                                                 long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.MONEY,
                    fields(Map.of("target", target == null ? "0" : target.toPlainString())),
                    amount == null ? BigDecimal.ZERO : amount, dueAt, componentId);
        }

        public static ComponentDraft experience(String componentId, int amount, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.EXPERIENCE, "",
                    BigDecimal.valueOf(Math.max(0, amount)), dueAt, componentId);
        }

        public static ComponentDraft experienceTarget(String componentId, int amount, int target, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.EXPERIENCE,
                    fields(Map.of("target", String.valueOf(Math.max(0, target)))),
                    BigDecimal.valueOf(Math.max(0, amount)), dueAt, componentId);
        }

        public static ComponentDraft vanillaItem(String componentId, Material material, int amount, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.VANILLA_ITEM,
                    encode(material == null ? "" : material.name()), BigDecimal.valueOf(Math.max(1, amount)),
                    dueAt, componentId);
        }

        public static ComponentDraft itemsAdderItem(String componentId, String itemId, int amount, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.ITEMSADDER_ITEM, encode(itemId),
                    BigDecimal.valueOf(Math.max(1, amount)), dueAt, componentId);
        }

        public static ComponentDraft booster(String componentId, boolean serverWide, BigDecimal multiplier,
                                             long durationMillis, String activatedBy, long dueAt) {
            String payload = fields(Map.of("server", String.valueOf(serverWide),
                    "multiplier", String.valueOf(multiplier), "duration", String.valueOf(durationMillis),
                    "activatedBy", activatedBy == null ? "reward-ledger" : activatedBy));
            return new ComponentDraft(componentId, serverWide ? RewardComponentType.SERVER_BOOSTER
                    : RewardComponentType.PERSONAL_BOOSTER, payload, multiplier, dueAt, componentId);
        }

        public static ComponentDraft permission(String componentId, String node, boolean grant, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.PERMISSION_ACTION,
                    fields(Map.of("node", node == null ? "" : node, "grant", String.valueOf(grant))),
                    BigDecimal.ZERO, dueAt, componentId);
        }

        public static ComponentDraft statistic(String componentId, String statistic, long amount, long dueAt) {
            return new ComponentDraft(componentId, RewardComponentType.STATISTIC_ADJUSTMENT,
                    fields(Map.of("statistic", statistic == null ? "" : statistic)),
                    BigDecimal.valueOf(amount), dueAt, componentId);
        }

        public RewardComponentRecord toRecord(String packageId, String sourceSystem, String sourceOperationId,
                                              UUID playerId, long now) {
            String safeComponentId = componentId.replaceAll("[^A-Za-z0-9_.:-]", "_");
            String safeIdempotency = (packageId + ':' + idempotencyKey).replaceAll("[^A-Za-z0-9_.:-]", "_");
            return new RewardComponentRecord(packageId, safeComponentId, sourceSystem, sourceOperationId,
                    playerId, payload == null ? "" : payload, type, amount == null ? BigDecimal.ZERO : amount,
                    dueAt <= 0L ? now : dueAt, RewardComponentState.PENDING, 0, now, 0L, 0L,
                    "", safeIdempotency.toLowerCase(Locale.ROOT), "", "", 0L, 0L, 0L, 0L);
        }
    }

    private record DeliveryResult(RewardComponentState state, String failure, long nextAttemptAt) {
        static DeliveryResult delivered() {
            return new DeliveryResult(RewardComponentState.DELIVERED, "", 0L);
        }

        static DeliveryResult retry(String failure) {
            long next = System.currentTimeMillis() + OFFLINE_RETRY_MILLIS;
            return new DeliveryResult(RewardComponentState.RETRY_READY, failure, next);
        }

        static DeliveryResult review(String failure) {
            return new DeliveryResult(RewardComponentState.STAFF_REVIEW, failure, 0L);
        }

        static DeliveryResult ambiguous(String failure) {
            return new DeliveryResult(RewardComponentState.AMBIGUOUS, failure, 0L);
        }
    }

    private static int intAmount(RewardComponentRecord component) {
        return Math.max(1, Math.min(64 * 36, component.amount().intValue()));
    }

    private static BigDecimal decimal(String value, BigDecimal fallback) {
        try {
            return value == null || value.isBlank() ? fallback : new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long longValue(String value, long fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int intField(String payload, String key, int fallback) {
        long value = longValue(fields(payload).get(key), fallback);
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? fallback : (int) value;
    }

    private static void repeat(long amount, Runnable action) {
        for (long index = 0; index < amount; index++) action.run();
    }

    private static String fields(Map<String, String> fields) {
        List<String> lines = new ArrayList<>();
        fields.forEach((key, value) -> lines.add(key + '=' + value.replace('\n', ' ').replace('\r', ' ')));
        return encode(String.join("\n", lines));
    }

    private static Map<String, String> fields(String payload) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (String line : decode(payload).split("\\R")) {
            int split = line.indexOf('=');
            if (split > 0) result.put(line.substring(0, split), line.substring(split + 1));
        }
        return result;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String payload) {
        if (payload == null || payload.isBlank()) return "";
        return new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
