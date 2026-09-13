package site.mcrelicworld.relicprison.selling;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.SellService;
import site.mcrelicworld.relicprison.api.event.RelicAutoSellEvent;
import site.mcrelicworld.relicprison.api.event.RelicSellEvent;
import site.mcrelicworld.relicprison.api.model.SellResult;
import site.mcrelicworld.relicprison.booster.BoosterItemService;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.economy.VaultEconomyAdapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SellServiceImpl implements SellService {
    private final RelicPrisonPlugin plugin;
    private final VaultEconomyAdapter economy;
    private final MultiplierServiceImpl multipliers;
    private final BoosterItemService boosterItems;
    private volatile SellPriceCatalog catalog;

    public SellServiceImpl(RelicPrisonPlugin plugin, VaultEconomyAdapter economy,
                           MultiplierServiceImpl multipliers, BoosterItemService boosterItems,
                           SellPriceCatalog catalog) {
        this.plugin = plugin; this.economy = economy; this.multipliers = multipliers;
        this.boosterItems = boosterItems; this.catalog = catalog;
    }

    public SellPriceCatalog catalog() { return catalog; }
    public void applyCatalog(SellPriceCatalog next) { catalog = next; }
    public SellPriceCatalog previewCatalog() throws Exception { return SellPriceCatalog.load(plugin); }

    @Override public CompletableFuture<BigDecimal> sellInventory(UUID playerId) {
        return sellInventoryDetailed(playerId).thenApply(SellResult::finalValue);
    }

    @Override public CompletableFuture<SellResult> sellInventoryDetailed(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return CompletableFuture.completedFuture(SellResult.failure("Player is not online"));
        return CompletableFuture.completedFuture(sellInventory(player, false));
    }

    public SellResult sellInventory(Player player, boolean automatic) {
        ensurePrimaryThread();
        SalePlan plan = planInventory(player.getInventory(), Scope.ALL);
        return executeInventoryPlan(player, plan, automatic);
    }

    public SellResult sellHand(Player player) {
        ensurePrimaryThread();
        SalePlan plan = planInventory(player.getInventory(), Scope.HAND);
        return executeInventoryPlan(player, plan, false);
    }

    /** Sells already-calculated mining drops without touching inventory. */
    public SellResult sellDrops(Player player, Collection<ItemStack> drops, boolean automatic) {
        return sellDrops(player, drops, automatic, true);
    }

    /** Sells already-calculated drops, optionally deferring statistic/profile recording to a caller transaction. */
    public SellResult sellDrops(Player player, Collection<ItemStack> drops, boolean automatic, boolean recordStatistics) {
        ensurePrimaryThread();
        SellResult planned = quoteDrops(player, drops);
        if (!planned.success()) return planned;
        BigDecimal finalValue = planned.finalValue();
        RelicSellEvent event = automatic
                ? new RelicAutoSellEvent(player.getUniqueId(), planned.baseValue(), finalValue)
                : new RelicSellEvent(player.getUniqueId(), planned.baseValue(), finalValue);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return SellResult.failure("Sale cancelled");
        VaultEconomyAdapter.Transaction transaction = economy.deposit(player, finalValue);
        if (!transaction.success()) return SellResult.failure(transaction.error());
        if (recordStatistics) recordMiningSale(player.getUniqueId(), finalValue, planned.itemCount());
        return planned;
    }

    public SellResult quoteDrops(Player player, Collection<ItemStack> drops) {
        ensurePrimaryThread();
        SaleQuote quote = quote(drops);
        if (quote.itemCount() == 0 || quote.baseValue().compareTo(catalog.minimumPayout()) < 0) {
            return SellResult.failure("Nothing sellable");
        }
        BigDecimal finalValue = round(quote.baseValue().multiply(multipliers.multiplier(player.getUniqueId())));
        return new SellResult(true, quote.baseValue(), finalValue, quote.itemCount(), "");
    }

    public void recordMiningSale(UUID playerId, BigDecimal finalValue, long itemCount) {
        recordEarnings(playerId, finalValue);
        plugin.invalidatePlaceholderCache(playerId);
        if (plugin.statistics() != null) plugin.statistics().recordItemsSold(playerId, itemCount);
    }

    private SellResult executeInventoryPlan(Player player, SalePlan plan, boolean automatic) {
        if (plan.lines().isEmpty() || plan.baseValue().compareTo(catalog.minimumPayout()) < 0) {
            return SellResult.failure("Nothing sellable");
        }
        PlayerInventory inventory = player.getInventory();
        for (SaleLine line : plan.lines()) {
            ItemStack current = inventory.getItem(line.slot());
            if (!sameStack(current, line.snapshot())) return SellResult.failure("Inventory changed before sale completed");
        }
        BigDecimal finalValue = round(plan.baseValue().multiply(multipliers.multiplier(player.getUniqueId())));
        RelicSellEvent event = automatic
                ? new RelicAutoSellEvent(player.getUniqueId(), plan.baseValue(), finalValue)
                : new RelicSellEvent(player.getUniqueId(), plan.baseValue(), finalValue);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return SellResult.failure("Sale cancelled");
        for (SaleLine line : plan.lines()) {
            ItemStack current = inventory.getItem(line.slot());
            if (!sameStack(current, line.snapshot())) return SellResult.failure("Inventory changed during sale event");
        }
        VaultEconomyAdapter.Transaction transaction = economy.deposit(player, finalValue);
        if (!transaction.success()) return SellResult.failure(transaction.error());
        for (SaleLine line : plan.lines()) inventory.setItem(line.slot(), null);
        recordEarnings(player.getUniqueId(), finalValue);
        plugin.invalidatePlaceholderCache(player.getUniqueId());
        if (plugin.statistics() != null) plugin.statistics().recordItemsSold(player.getUniqueId(), plan.itemCount());
        return new SellResult(true, plan.baseValue(), finalValue, plan.itemCount(), "");
    }

    private SalePlan planInventory(PlayerInventory inventory, Scope scope) {
        List<SaleLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        int start = scope == Scope.HAND ? inventory.getHeldItemSlot() : 0;
        int end = scope == Scope.HAND ? start + 1 : inventory.getStorageContents().length;
        for (int slot = start; slot < end; slot++) {
            ItemStack item = inventory.getItem(slot);
            BigDecimal value = itemValue(item);
            if (value.signum() <= 0) continue;
            ItemStack copy = item.clone();
            lines.add(new SaleLine(slot, copy));
            total = total.add(value);
            count += item.getAmount();
        }
        return new SalePlan(List.copyOf(lines), total, count);
    }

    public DropPartition partition(Collection<ItemStack> items) {
        List<ItemStack> sellable = new ArrayList<>();
        List<ItemStack> unsellable = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0 || item.getType().isAir()) continue;
            (sellable(item) ? sellable : unsellable).add(item.clone());
        }
        return new DropPartition(List.copyOf(sellable), List.copyOf(unsellable));
    }

    public SaleQuote quote(Collection<ItemStack> items) {
        EnumMap<Material, Integer> consolidated = new EnumMap<>(Material.class);
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (ItemStack item : items) {
            if (!sellable(item)) continue;
            BigDecimal price = unitPrice(item);
            if (price.signum() <= 0) continue;
            if (plugin.itemsAdder().customItemId(item).isEmpty()) consolidated.merge(item.getType(), item.getAmount(), Integer::sum);
            total = total.add(price.multiply(BigDecimal.valueOf(item.getAmount())));
            count += item.getAmount();
        }
        return new SaleQuote(total, count, Map.copyOf(consolidated));
    }

    public BigDecimal baseValue(ItemStack item) { return itemValue(item); }

    @Override public BigDecimal estimatedInventoryValue(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return BigDecimal.ZERO;
        SalePlan plan = planInventory(player.getInventory(), Scope.ALL);
        return round(plan.baseValue().multiply(multipliers.multiplier(playerId)));
    }

    public BigDecimal estimatedInventoryBaseValue(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? BigDecimal.ZERO : planInventory(player.getInventory(), Scope.ALL).baseValue();
    }

    @Override public BigDecimal estimatedHeldValue(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return BigDecimal.ZERO;
        return round(itemValue(player.getInventory().getItemInMainHand()).multiply(multipliers.multiplier(playerId)));
    }

    public BigDecimal estimatedHeldBaseValue(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? BigDecimal.ZERO : itemValue(player.getInventory().getItemInMainHand());
    }

    private BigDecimal itemValue(ItemStack item) {
        if (!sellable(item)) return BigDecimal.ZERO;
        return unitPrice(item).multiply(BigDecimal.valueOf(item.getAmount()));
    }

    private BigDecimal unitPrice(ItemStack item) {
        java.util.Optional<String> customId = plugin.config().snapshot().features().itemsAdder() ? plugin.itemsAdder().customItemId(item) : java.util.Optional.empty();
        if (customId.isPresent()) return catalog.customPrice(customId.get()).orElse(BigDecimal.ZERO);
        return catalog.price(item.getType()).orElse(BigDecimal.ZERO);
    }

    private boolean sellable(ItemStack item) {
        if (item == null || item.getAmount() <= 0 || item.getType().isAir()) return false;
        if (boosterItems.isProtected(item)) return false;
        java.util.Optional<String> customId = plugin.config().snapshot().features().itemsAdder() ? plugin.itemsAdder().customItemId(item) : java.util.Optional.empty();
        if (customId.isPresent()) {
            if (!catalog.allowCustomItemsWithLore() && item.hasItemMeta() && item.getItemMeta().hasLore()) return false;
            return catalog.customPrice(customId.get()).isPresent();
        }
        if (!catalog.allowItemsWithLore() && item.hasItemMeta() && item.getItemMeta().hasLore()) return false;
        return catalog.price(item.getType()).isPresent();
    }

    private BigDecimal round(BigDecimal amount) {
        return amount.setScale(catalog.roundingScale(), RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private void recordEarnings(UUID playerId, BigDecimal amount) {
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(playerId).orElse(null);
        if (profile != null) profile.addMoneyEarned(amount);
        if (plugin.gangs() != null && amount.signum() > 0) plugin.gangs().recordContribution(playerId,
                site.mcrelicworld.relicprison.gang.GangConfig.ContributionType.MONEY, amount, "",
                "sale:" + UUID.randomUUID()).exceptionally(error -> null);
    }

    private static boolean sameStack(ItemStack current, ItemStack snapshot) {
        return current != null && current.getAmount() == snapshot.getAmount() && current.isSimilar(snapshot);
    }

    private static void ensurePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Inventory sales must run on the server thread");
    }

    private enum Scope { ALL, HAND }
    private record SaleLine(int slot, ItemStack snapshot) {}
    private record SalePlan(List<SaleLine> lines, BigDecimal baseValue, int itemCount) {}
    public record SaleQuote(BigDecimal baseValue, int itemCount, Map<Material, Integer> itemAmounts) {}
    public record DropPartition(List<ItemStack> sellable, List<ItemStack> unsellable) {}
}
