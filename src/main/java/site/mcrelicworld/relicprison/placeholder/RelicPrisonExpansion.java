package site.mcrelicworld.relicprison.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.model.LeaderboardEntry;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.leaderboard.LeaderboardBoard;
import site.mcrelicworld.relicprison.leaderboard.LeaderboardCatalog;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.progression.PrestigeDefinition;
import site.mcrelicworld.relicprison.progression.RankDefinition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** PlaceholderAPI expansion verified against PlaceholderAPI 2.11.6. */
public final class RelicPrisonExpansion extends PlaceholderExpansion {
    private static final Pattern TOP_NAME = Pattern.compile("(.+)_top_name_(\\d+)$");
    private static final Pattern TOP_VALUE = Pattern.compile("(.+)_top_value_(\\d+)$");

    private final RelicPrisonPlugin plugin;
    private final ConcurrentHashMap<UUID, ValueCache> valueCache = new ConcurrentHashMap<>();
    private final AtomicLong slowEvaluationCount = new AtomicLong();
    private final AtomicLong slowestEvaluationMillis = new AtomicLong();
    private final AtomicReference<String> lastSlowPlaceholder = new AtomicReference<>("");

    public RelicPrisonExpansion(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "relicprison"; }
    @Override public String getAuthor() { return "Andrew"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override public String onRequest(OfflinePlayer offline, String parameters) {
        long started = System.nanoTime();
        try {
            return resolve(offline, parameters);
        } catch (RuntimeException ex) {
            return malformed();
        } finally {
            long millis = (System.nanoTime() - started) / 1_000_000L;
            if (millis >= plugin.config().snapshot().placeholders().slowThresholdMillis()) {
                slowEvaluationCount.incrementAndGet();
                slowestEvaluationMillis.accumulateAndGet(millis, Math::max);
                lastSlowPlaceholder.set(parameters == null ? "" : parameters);
            }
        }
    }

    private String resolve(OfflinePlayer offline, String parameters) {
        if (offline == null || parameters == null) return "";
        String key = parameters.toLowerCase(Locale.ROOT);
        String gang = gangPlaceholder(offline.getUniqueId(), key);
        if (gang != null) return gang;
        if (key.equals("balance")) return plugin.numbers().currency(currentBalance(offline));
        if (key.equals("balance_short")) return plugin.numbers().abbreviatedCurrency(currentBalance(offline));
        if (key.equals("balance_raw")) return plugin.numbers().plain(currentBalance(offline));
        String dynamic = dynamicPlaceholder(offline, key);
        if (dynamic != null) return dynamic;
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(offline.getUniqueId()).orElse(null);
        if (profile == null) return key.equals("loaded") ? "false" : unavailable();
        if (key.equals("loaded")) return "true";
        RankDefinition rank = plugin.rankService().definition(profile.currentRank()).orElse(null);
        PrestigeDefinition prestige = plugin.prestigeService().definition(profile.currentPrestige()).orElse(null);
        Player player = offline.getPlayer();
        MineDefinition mine = currentMine(player);
        return switch (key) {
            case "rank" -> profile.currentRank();
            case "rank_display" -> rank == null ? profile.currentRank() : rank.displayName();
            case "next_rank" -> plugin.rankService().next(profile.currentRank()).map(RankDefinition::id).orElse("max");
            case "next_rank_display" -> plugin.rankService().next(profile.currentRank())
                    .map(RankDefinition::displayName).orElse("MAX");
            case "rank_cost" -> plugin.numbers().currency(rankCost(profile, rank));
            case "rank_cost_raw" -> rankCost(profile, rank).toPlainString();
            case "rank_progress_percent" -> progressPercent(currentBalance(offline), rankCost(profile, rank));
            case "rank_money_remaining" -> plugin.numbers().currency(remaining(currentBalance(offline),
                    rankCost(profile, rank)));
            case "prestige" -> profile.currentPrestige() == null ? "none" : profile.currentPrestige();
            case "prestige_display" -> prestige == null ? "None" : prestige.displayName();
            case "next_prestige" -> plugin.prestigeService().next(profile.currentPrestige())
                    .map(PrestigeDefinition::id).orElse("max");
            case "next_prestige_cost" -> plugin.prestigeService().next(profile.currentPrestige())
                    .map(value -> plugin.numbers().currency(value.cost())).orElse("MAX");
            case "prestige_money_remaining" -> plugin.numbers().currency(remaining(currentBalance(offline),
                    plugin.prestigeService().next(profile.currentPrestige()).map(PrestigeDefinition::cost)
                            .orElse(BigDecimal.ZERO)));
            case "blocks", "blocks_lifetime" -> plugin.numbers().full(profile.lifetimeBlocks());
            case "blocks_daily" -> plugin.numbers().full(profile.dailyBlocks());
            case "blocks_weekly" -> plugin.numbers().full(profile.weeklyBlocks());
            case "blocks_monthly" -> plugin.numbers().full(profile.monthlyBlocks());
            case "money_earned" -> plugin.numbers().currency(profile.moneyEarned());
            case "multiplier" -> plugin.multiplierService().multiplier(offline.getUniqueId())
                    .stripTrailingZeros().toPlainString();
            case "sell_value" -> plugin.numbers().currency(plugin.sellService()
                    .estimatedInventoryValue(offline.getUniqueId()));
            case "inventory_base_value" -> plugin.numbers().currency(values(offline).inventoryBase());
            case "inventory_final_value" -> plugin.numbers().currency(values(offline).inventoryFinal());
            case "hand_base_value" -> plugin.numbers().currency(values(offline).handBase());
            case "hand_final_value" -> plugin.numbers().currency(values(offline).handFinal());
            case "personal_booster_multiplier" -> plugin.boosterService().strongestPersonalMultiplier(offline.getUniqueId())
                    .stripTrailingZeros().toPlainString();
            case "personal_booster_time" -> duration(plugin.boosterService()
                    .longestPersonalRemainingMillis(offline.getUniqueId()));
            case "server_booster_multiplier" -> plugin.boosterService().strongestServerMultiplier()
                    .stripTrailingZeros().toPlainString();
            case "server_booster_time" -> duration(plugin.boosterService().longestServerRemainingMillis());
            case "combined_multiplier" -> plugin.multiplierService().multiplier(offline.getUniqueId())
                    .stripTrailingZeros().toPlainString();
            case "booster_count" -> String.valueOf(plugin.boosterService().activeFor(offline.getUniqueId()).size());
            case "mine", "mine_id" -> mine == null ? "none" : mine.id();
            case "mine_name" -> mine == null ? "None" : mine.displayName();
            case "mine_remaining" -> mine == null ? "0" : plugin.numbers()
                    .full(Math.max(0, plugin.mineResets().remainingBlocks(mine.id())));
            case "mine_remaining_percent" -> mine == null ? "0" : remainingPercent(mine);
            case "mine_mined_percent" -> mine == null ? "0" : String.format(Locale.US, "%.2f",
                    plugin.mineResets().minedPercentage(mine.id()));
            case "mine_reset_state" -> mine == null ? "none" : plugin.mineResets().state(mine.id())
                    .map(state -> state.state().name().toLowerCase(Locale.ROOT)).orElse("unknown");
            case "mine_reset_count" -> mine == null ? "0" : plugin.numbers()
                    .full(Math.max(0, plugin.mineResets().resetCount(mine.id())));
            case "mine_last_reset_duration" -> mine == null ? "0s" : duration(plugin.mineResets()
                    .lastResetDurationMillis(mine.id()));
            case "mine_next_reset" -> mine == null ? "0s" : duration(Math.max(0,
                    plugin.mineResets().nextReset(mine.id()) - System.currentTimeMillis()));
            case "items_sold" -> plugin.numbers().full(plugin.statistics().itemsSold(offline.getUniqueId()));
            case "rankups" -> plugin.numbers().full(plugin.statistics().rankups(offline.getUniqueId()));
            case "prestiges_count" -> plugin.numbers().full(plugin.statistics().prestiges(offline.getUniqueId()));
            case "boosters_used" -> plugin.numbers().full(plugin.statistics().boostersUsed(offline.getUniqueId()));
            case "playtime" -> duration(plugin.statistics().playtimeSeconds(offline.getUniqueId()) * 1000L);
            default -> null;
        };
    }

    private String dynamicPlaceholder(OfflinePlayer offline, String key) {
        UUID playerId = offline.getUniqueId();
        if (key.startsWith("blocks_material_")) {
            String raw = key.substring("blocks_material_".length());
            Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (material == null || !material.isBlock()) return "0";
            return plugin.numbers().full(plugin.statistics().miningStat(playerId, "lifetime", "material",
                    material.name().toLowerCase(Locale.ROOT)));
        }
        if (key.startsWith("blocks_mine_")) {
            String raw = key.substring("blocks_mine_".length());
            String mineId;
            try {
                mineId = MineDefinition.normalizeId(raw);
            } catch (IllegalArgumentException ex) {
                return "0";
            }
            if (plugin.mineService().findMine(mineId).isEmpty()) return "0";
            return plugin.numbers().full(plugin.statistics().miningStat(playerId, "lifetime", "mine", mineId));
        }
        if (key.equals("normal_blocks")) {
            return plugin.numbers().full(plugin.statistics().miningStat(playerId, "lifetime", "source", "normal"));
        }
        if (key.equals("bulk_blocks")) {
            return plugin.numbers().full(plugin.statistics().miningStat(playerId, "lifetime", "source", "bulk"));
        }
        if (key.startsWith("leaderboard_")) return leaderboardPlaceholder(playerId, key.substring("leaderboard_".length()));
        return null;
    }

    private String gangPlaceholder(UUID playerId, String key) {
        if (!key.startsWith("gang_")) return null;
        var gang = plugin.gangs() == null ? null : plugin.gangs().cachedGang(playerId).orElse(null);
        var rank = plugin.gangs() == null ? null : plugin.gangs().cachedRank(playerId).orElse(null);
        var statistics = plugin.gangs() == null ? null : plugin.gangs().cachedStatistics(playerId).orElse(null);
        var contribution = plugin.gangs() == null ? null : plugin.gangs().cachedMemberStatistics(playerId).orElse(null);
        String unavailable = plugin.gangs() == null ? "none" : plugin.gangs().config().unavailablePlaceholder();
        return site.mcrelicworld.relicprison.gang.GangPlaceholderValues.resolve(key, gang, rank, statistics,
                contribution, plugin.gangs() == null ? 0 : plugin.gangs().cachedOnlineMembers(playerId),
                plugin.gangs() == null ? Map.of() : plugin.gangs().cachedLeaderboardPositions(playerId),
                plugin.numbers()::currency, unavailable);
    }

    private String leaderboardPlaceholder(UUID playerId, String body) {
        if (body.endsWith("_position")) {
            LeaderboardTarget target = target(body.substring(0, body.length() - "_position".length()));
            Optional<List<LeaderboardEntry>> entries = plugin.statistics().cachedLeaderboard(target.board().metric(),
                    target.period(), plugin.config().snapshot().leaderboards().snapshotSize());
            if (entries.isEmpty()) return unavailable();
            return entries.get().stream().filter(entry -> entry.playerId().equals(playerId))
                    .findFirst().map(entry -> String.valueOf(entry.position())).orElse("unranked");
        }
        Matcher topName = TOP_NAME.matcher(body);
        if (topName.matches()) {
            LeaderboardEntry entry = leaderboardEntry(topName.group(1), topName.group(2)).orElse(null);
            return entry == null ? unavailable() : entry.playerName();
        }
        Matcher topValue = TOP_VALUE.matcher(body);
        if (topValue.matches()) {
            LeaderboardTarget target = target(topValue.group(1));
            LeaderboardEntry entry = leaderboardEntry(target, topValue.group(2)).orElse(null);
            return entry == null ? unavailable() : formatLeaderboardValue(target.board().metric(), entry.value());
        }
        if (body.endsWith("_type")) return target(body.substring(0, body.length() - "_type".length())).board().metric();
        if (body.endsWith("_period")) return target(body.substring(0, body.length() - "_period".length())).period();
        return malformed();
    }

    private Optional<LeaderboardEntry> leaderboardEntry(String boardPart, String rawPosition) {
        return leaderboardEntry(target(boardPart), rawPosition);
    }

    private Optional<LeaderboardEntry> leaderboardEntry(LeaderboardTarget target, String rawPosition) {
        int position;
        try {
            position = Integer.parseInt(rawPosition);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        if (position <= 0) return Optional.empty();
        return plugin.statistics().cachedLeaderboard(target.board().metric(), target.period(), position)
                .flatMap(entries -> entries.stream().filter(entry -> entry.position() == position).findFirst());
    }

    private LeaderboardTarget target(String rawBoard) {
        String boardId = rawBoard;
        String periodOverride = null;
        int seasonIndex = rawBoard.indexOf("_season_");
        if (seasonIndex >= 0) {
            boardId = rawBoard.substring(0, seasonIndex);
            periodOverride = "season:" + rawBoard.substring(seasonIndex + "_season_".length());
        }
        LeaderboardCatalog catalog = plugin.statistics().leaderboardCatalog();
        LeaderboardBoard board = catalog.board(boardId).orElseThrow(() ->
                new IllegalArgumentException("Unknown leaderboard board: " + rawBoard));
        return new LeaderboardTarget(board, periodOverride == null ? board.period() : periodOverride);
    }

    public Map<String, String> diagnosticValues() {
        return Map.of(
                "placeholder-slow-count", String.valueOf(slowEvaluationCount.get()),
                "placeholder-slowest-ms", String.valueOf(slowestEvaluationMillis.get()),
                "placeholder-last-slow", lastSlowPlaceholder.get()
        );
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) valueCache.remove(playerId);
    }

    public void invalidateAll() {
        valueCache.clear();
    }

    private BigDecimal currentBalance(OfflinePlayer offline) {
        Player player = offline.getPlayer();
        return player == null ? BigDecimal.ZERO : plugin.economy().balance(player);
    }

    private BigDecimal rankCost(PlayerProfile profile, RankDefinition rank) {
        if (rank == null || plugin.rankService().next(rank.id()).isEmpty()) return BigDecimal.ZERO;
        return rank.nextCost().multiply(plugin.prestigeService().rankCostMultiplier(profile.currentPrestige()));
    }

    private String progressPercent(BigDecimal balance, BigDecimal cost) {
        if (cost == null || cost.signum() <= 0) return "100.00";
        BigDecimal percent = balance.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(100))
                .divide(cost, 2, RoundingMode.HALF_UP);
        if (percent.compareTo(BigDecimal.valueOf(100)) > 0) percent = BigDecimal.valueOf(100);
        return percent.toPlainString();
    }

    private BigDecimal remaining(BigDecimal balance, BigDecimal cost) {
        if (cost == null || cost.signum() <= 0) return BigDecimal.ZERO;
        return cost.subtract(balance == null ? BigDecimal.ZERO : balance).max(BigDecimal.ZERO);
    }

    private String remainingPercent(MineDefinition mine) {
        if (mine.volume() <= 0) return "0";
        double percent = Math.max(0.0, Math.min(100.0, plugin.mineResets().remainingBlocks(mine.id()) * 100.0
                / mine.volume()));
        return String.format(Locale.US, "%.2f", percent);
    }

    private ValueSnapshot values(OfflinePlayer offline) {
        UUID playerId = offline.getUniqueId();
        long now = System.currentTimeMillis();
        ValueCache cached = valueCache.get(playerId);
        if (cached != null && cached.expiresAt() > now) return cached.snapshot();
        ValueSnapshot calculated = calculateValues(playerId);
        valueCache.put(playerId, new ValueCache(calculated,
                now + plugin.config().snapshot().placeholders().valueCacheMillis()));
        if (valueCache.size() > 512) {
            valueCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
        return calculated;
    }

    private ValueSnapshot calculateValues(UUID playerId) {
        return new ValueSnapshot(
                plugin.sellService().estimatedInventoryBaseValue(playerId),
                plugin.sellService().estimatedInventoryValue(playerId),
                plugin.sellService().estimatedHeldBaseValue(playerId),
                plugin.sellService().estimatedHeldValue(playerId)
        );
    }

    private MineDefinition currentMine(Player player) {
        if (player == null) return null;
        return plugin.mineService().mineAt(player.getWorld().getUID(), player.getLocation().getBlockX(),
                player.getLocation().getBlockY(), player.getLocation().getBlockZ()).orElse(null);
    }

    private String formatLeaderboardValue(String metric, BigDecimal value) {
        return switch (metric) {
            case "rank" -> rankDisplay(value.intValue());
            case "prestige" -> prestigeDisplay(value.intValue());
            case "money" -> plugin.numbers().currency(value);
            case "playtime" -> duration(value.longValue() * 1000L);
            default -> plugin.numbers().full(value);
        };
    }

    private String rankDisplay(int index) {
        List<RankDefinition> ranks = plugin.rankService().definitions();
        return index >= 0 && index < ranks.size() ? ranks.get(index).displayName() : "unknown";
    }

    private String prestigeDisplay(int value) {
        if (value <= 0) return "none";
        List<PrestigeDefinition> prestiges = plugin.prestigeService().definitions();
        int index = value - 1;
        return index >= 0 && index < prestiges.size() ? prestiges.get(index).displayName() : "unknown";
    }

    private String unavailable() {
        return plugin.config().snapshot().placeholders().unavailableText();
    }

    private String malformed() {
        return plugin.config().snapshot().placeholders().malformedText();
    }

    private static String duration(long millis) {
        Duration value = Duration.ofMillis(Math.max(0L, millis));
        long days = value.toDays();
        long hours = value.minusDays(days).toHours();
        long minutes = value.minusDays(days).minusHours(hours).toMinutes();
        long seconds = value.minusDays(days).minusHours(hours).minusMinutes(minutes).toSeconds();
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private record ValueSnapshot(BigDecimal inventoryBase, BigDecimal inventoryFinal,
                                 BigDecimal handBase, BigDecimal handFinal) { }
    private record ValueCache(ValueSnapshot snapshot, long expiresAt) { }
    private record LeaderboardTarget(LeaderboardBoard board, String period) { }
}
