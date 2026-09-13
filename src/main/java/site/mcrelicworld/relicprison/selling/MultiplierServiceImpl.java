package site.mcrelicworld.relicprison.selling;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.MultiplierService;
import site.mcrelicworld.relicprison.booster.ActiveBooster;
import site.mcrelicworld.relicprison.booster.BoosterConfig;
import site.mcrelicworld.relicprison.booster.BoosterConfigRepository;
import site.mcrelicworld.relicprison.booster.BoosterServiceImpl;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.progression.PrestigeDefinition;
import site.mcrelicworld.relicprison.progression.RankDefinition;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MultiplierServiceImpl implements MultiplierService {
    private static final MathContext MATH = MathContext.DECIMAL64;
    private final RelicPrisonPlugin plugin;
    private final BoosterServiceImpl boosters;
    private final BoosterConfigRepository configs;
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    public MultiplierServiceImpl(RelicPrisonPlugin plugin, BoosterServiceImpl boosters, BoosterConfigRepository configs) {
        this.plugin = plugin; this.boosters = boosters; this.configs = configs;
    }

    @Override public BigDecimal multiplier(UUID playerId) {
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(playerId);
        if (cached != null && cached.expiresAt > now) return cached.value;
        BigDecimal calculated = calculate(playerId);
        cache.put(playerId, new CacheEntry(calculated, now + 500L));
        return calculated;
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
        plugin.invalidatePlaceholderCache(playerId);
    }
    public void invalidateAll() {
        cache.clear();
        plugin.invalidateAllPlaceholderCaches();
    }

    private BigDecimal calculate(UUID playerId) {
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(playerId).orElse(null);
        BigDecimal rank = profile == null ? BigDecimal.ONE : plugin.rankService().definition(profile.currentRank())
                .map(RankDefinition::sellMultiplier).orElse(BigDecimal.ONE);
        BigDecimal prestige = profile == null || profile.currentPrestige() == null ? BigDecimal.ONE
                : plugin.prestigeService().definition(profile.currentPrestige()).map(PrestigeDefinition::sellMultiplier).orElse(BigDecimal.ONE);
        BigDecimal permanent = boosters.cachedPermanentMultiplier(playerId);
        List<BigDecimal> personalBoosters = boosters.personalBoosters(playerId).stream().map(ActiveBooster::multiplier).toList();
        List<BigDecimal> serverBoosters = boosters.serverBoosterRecords().stream().map(ActiveBooster::multiplier).toList();
        BigDecimal permission = permissionMultiplier(playerId);
        BigDecimal gang = boosters.gangMultiplier(playerId,
                site.mcrelicworld.relicprison.gang.GangBooster.Type.SELL);
        BigDecimal result = stack(List.of(rank, prestige, permission, permanent, gang), personalBoosters, serverBoosters);
        return result.min(configs.config().maximumFinalMultiplier()).max(BigDecimal.ONE).stripTrailingZeros();
    }

    private BigDecimal permissionMultiplier(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        BigDecimal best = BigDecimal.ONE;
        for (var entry : configs.config().permissionMultipliers().entrySet()) {
            if (player.isOnline() && player.getPlayer() != null && player.getPlayer().hasPermission(entry.getKey())) {
                best = best.max(entry.getValue());
            }
        }
        return best;
    }

    private BigDecimal stack(List<BigDecimal> permanent, List<BigDecimal> personal, List<BigDecimal> server) {
        BoosterConfig.StackingMode mode = configs.config().stackingMode();
        List<BigDecimal> all = new ArrayList<>(permanent);
        all.addAll(personal); all.addAll(server);
        return switch (mode) {
            case MULTIPLY_ALL -> all.stream().reduce(BigDecimal.ONE, (a, b) -> a.multiply(b, MATH));
            case ADD_BONUSES -> BigDecimal.ONE.add(all.stream().map(v -> v.subtract(BigDecimal.ONE))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            case HIGHEST_ONLY -> all.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
            case HIGHEST_PER_CATEGORY -> permanent.stream().reduce(BigDecimal.ONE, (a, b) -> a.multiply(b, MATH))
                    .multiply(personal.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE), MATH)
                    .multiply(server.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE), MATH);
        };
    }

    private record CacheEntry(BigDecimal value, long expiresAt) {}
}
