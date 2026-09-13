package site.mcrelicworld.relicprison.statistics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.StatisticsService;
import site.mcrelicworld.relicprison.api.model.LeaderboardEntry;
import site.mcrelicworld.relicprison.database.DatabaseManager;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.leaderboard.LeaderboardBoard;
import site.mcrelicworld.relicprison.leaderboard.LeaderboardCatalog;
import site.mcrelicworld.relicprison.leaderboard.LeaderboardPeriods;
import site.mcrelicworld.relicprison.mining.BulkMiningCommitRepository;
import site.mcrelicworld.relicprison.mining.BulkMiningCommittedResult;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Buffered statistics writer designed to avoid one database operation per mined block. */
public final class StatisticsServiceImpl implements StatisticsService {
    private static final int MAX_PLAYER_COUNTER_CACHE = 5000;
    private static final long PLAYER_COUNTER_IDLE_MILLIS = 10 * 60 * 1000L;

    private final RelicPrisonPlugin plugin;
    private final DatabaseManager database;
    private final PlayerMiningStatisticsService playerMiningStatistics;
    private final MineAnalyticsService mineAnalytics;
    private final Map<String, Long> mineTotals = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> minePending = new ConcurrentHashMap<>();
    private final Map<PlayerMineKey, LongAdder> playerMinePending = new ConcurrentHashMap<>();
    private final Map<MiningStatKey, Long> miningTotals = new ConcurrentHashMap<>();
    private final Map<MiningStatKey, LongAdder> miningPending = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerCounters> playerCounters = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCounters> playerPending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inactiveSince = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> miningStatsLoadedPlayers = ConcurrentHashMap.newKeySet();
    private final LongAdder failedEvictionFlushes = new LongAdder();
    private final Map<LeaderboardRequest, SnapshotCache> leaderboardCache = new ConcurrentHashMap<>();
    private final Map<LeaderboardRequest, CompletableFuture<List<LeaderboardEntry>>> leaderboardLoads =
            new ConcurrentHashMap<>();
    private volatile LeaderboardCatalog leaderboardCatalog = new LeaderboardCatalog(Map.of());
    private int flushTask = -1;
    private int leaderboardTask = -1;

    public StatisticsServiceImpl(RelicPrisonPlugin plugin, DatabaseManager database) {
        this.plugin = plugin; this.database = database;
        this.playerMiningStatistics = new PlayerMiningStatisticsService(plugin);
        this.mineAnalytics = new MineAnalyticsService(plugin);
    }

    public void initialize() {
        initializeAsync().whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().warning("Statistics initialization failed: " + rootMessage(error));
                return;
            }
            startFlushTask();
        }));
    }

    public CompletableFuture<Void> initializeAsync() {
        try {
            leaderboardCatalog = LeaderboardCatalog.load(plugin.getDataFolder());
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
        return database.<Void>submitIdempotent(connection -> {
            if (!mineAnalytics.enabled()) return null;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT mine_id,lifetime_blocks FROM rp_mine_statistics");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) mineTotals.put(result.getString(1), result.getLong(2));
                return null;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        }).orTimeout(15, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void startFlushTask() {
        if (flushTask != -1) Bukkit.getScheduler().cancelTask(flushTask);
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sessions.putIfAbsent(player.getUniqueId(), now);
            loadPlayer(player.getUniqueId());
        }
        flushTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::flushAsync, 100L, 100L);
        startLeaderboardTask();
    }

    public void shutdown() {
        if (flushTask != -1) Bukkit.getScheduler().cancelTask(flushTask);
        if (leaderboardTask != -1) Bukkit.getScheduler().cancelTask(leaderboardTask);
        long now = System.currentTimeMillis();
        for (UUID playerId : List.copyOf(sessions.keySet())) closeSession(playerId, now);
        flush().orTimeout(plugin.config().snapshot().storage().shutdownFlushTimeoutSeconds(),
                java.util.concurrent.TimeUnit.SECONDS).exceptionally(error -> {
                    plugin.getLogger().severe("Statistics shutdown flush failed: " + rootMessage(error));
                    return null;
                });
    }

    public void playerJoin(UUID playerId) {
        inactiveSince.remove(playerId);
        sessions.put(playerId, System.currentTimeMillis());
        loadPlayer(playerId);
    }

    public void playerQuit(UUID playerId) {
        long now = System.currentTimeMillis();
        closeSession(playerId, now);
        inactiveSince.put(playerId, now);
        flush().whenComplete((ignored, error) -> {
            if (error != null) {
                failedEvictionFlushes.increment();
                return;
            }
            evictInactive(System.currentTimeMillis());
        });
    }

    private void closeSession(UUID playerId, long now) {
        Long joined = sessions.remove(playerId);
        if (joined == null) return;
        long seconds = Math.max(0L, (now - joined) / 1000L);
        if (seconds > 0) pending(playerId).playtime.add(seconds);
    }

    public void recordBlocks(UUID playerId, String mineId, long amount) {
        if (amount <= 0 || mineId == null) return;
        if (!mineAnalytics.enabled()) return;
        String normalized = mineId.toLowerCase(Locale.ROOT);
        mineTotals.merge(normalized, amount, Long::sum);
        minePending.computeIfAbsent(normalized, ignored -> new LongAdder()).add(amount);
        playerMinePending.computeIfAbsent(new PlayerMineKey(playerId, normalized), ignored -> new LongAdder()).add(amount);
    }

    public void recordMining(UUID playerId, String mineId, Map<Material, Integer> materials,
                             boolean bulk, boolean autoSell, boolean autoPickup) {
        long total = materials == null ? 0L : materials.values().stream().mapToLong(Integer::longValue).sum();
        recordMining(playerId, mineId, materials, Map.of(), bulk, autoSell ? total : 0L,
                autoPickup ? total : 0L, 0L);
    }

    public void recordMining(UUID playerId, String mineId, Map<Material, Integer> materials,
                             Map<String, Integer> customBlocks, boolean bulk, long autoSellBlocks,
                             long autoPickupBlocks, long autoBlockBlocks) {
        recordMining(playerId, mineId, materials, customBlocks, bulk, autoSellBlocks, autoPickupBlocks,
                autoBlockBlocks, 0L);
    }

    public void recordMining(UUID playerId, String mineId, Map<Material, Integer> materials,
                             Map<String, Integer> customBlocks, boolean bulk, long autoSellBlocks,
                             long autoPickupBlocks, long autoBlockBlocks, long fallbackDroppedItems) {
        if (playerId == null || mineId == null || materials == null || materials.isEmpty()) return;
        if (!playerMiningStatistics.enabled()) return;
        long total = materials.values().stream().mapToLong(Integer::longValue).sum();
        if (total <= 0) return;
        PeriodKeys periods = periods(System.currentTimeMillis());
        List<PeriodKey> periodKeys = List.of(
                new PeriodKey("daily", periods.daily()),
                new PeriodKey("weekly", periods.weekly()),
                new PeriodKey("monthly", periods.monthly()),
                new PeriodKey("lifetime", "lifetime"));
        if (plugin.config().snapshot().leaderboards().seasonalEnabled()) {
            long now = System.currentTimeMillis();
            if (!java.time.Instant.ofEpochMilli(now).isBefore(plugin.config().snapshot().leaderboards().seasonStart())
                    && java.time.Instant.ofEpochMilli(now).isBefore(plugin.config().snapshot().leaderboards().seasonEnd())) {
                periodKeys = new ArrayList<>(periodKeys);
                periodKeys.add(new PeriodKey("season", plugin.config().snapshot().leaderboards().seasonId()));
            }
        }
        Map<MiningStatisticsDimensions.Key, Long> dimensions = MiningStatisticsDimensions.committed(mineId, materials,
                customBlocks, bulk, autoSellBlocks, autoPickupBlocks, autoBlockBlocks, fallbackDroppedItems);
        for (PeriodKey period : periodKeys) {
            for (var dimension : dimensions.entrySet()) {
                miningPending.computeIfAbsent(new MiningStatKey(playerId, period.type(), period.key(),
                        dimension.getKey().type(), dimension.getKey().key()), ignored -> new LongAdder())
                        .add(dimension.getValue());
                miningTotals.merge(new MiningStatKey(playerId, period.type(), period.key(),
                        dimension.getKey().type(), dimension.getKey().key()), dimension.getValue(), Long::sum);
            }
        }
    }

    public void recordItemsSold(UUID playerId, long amount) { if (amount > 0) pending(playerId).itemsSold.add(amount); }

    public List<BulkMiningCommittedResult.Period> committedPeriods(long epochMillis) {
        PeriodKeys keys = periods(epochMillis);
        List<BulkMiningCommittedResult.Period> result = new ArrayList<>(List.of(
                new BulkMiningCommittedResult.Period("daily", keys.daily()),
                new BulkMiningCommittedResult.Period("weekly", keys.weekly()),
                new BulkMiningCommittedResult.Period("monthly", keys.monthly()),
                new BulkMiningCommittedResult.Period("lifetime", "lifetime")));
        if (plugin.config().snapshot().leaderboards().seasonalEnabled()) {
            Instant instant = Instant.ofEpochMilli(epochMillis);
            if (!instant.isBefore(plugin.config().snapshot().leaderboards().seasonStart())
                    && instant.isBefore(plugin.config().snapshot().leaderboards().seasonEnd())) {
                result.add(new BulkMiningCommittedResult.Period("season",
                        plugin.config().snapshot().leaderboards().seasonId()));
            }
        }
        return List.copyOf(result);
    }

    public void reflectCommittedBulk(UUID playerId, BulkMiningCommittedResult result) {
        if (result.itemsSold() > 0) {
            playerCounters.merge(playerId, new PlayerCounters(result.itemsSold(), 0L, 0L, 0L, 0L),
                    PlayerCounters::add);
        }
        for (BulkMiningCommittedResult.MineResult mine : result.mines()) {
            if (result.mineAnalytics()) mineTotals.merge(mine.mineId().toLowerCase(Locale.ROOT), mine.blocks(), Long::sum);
            Map<BulkMiningCommitRepository.Dimension, Long> dimensions = BulkMiningCommitRepository.dimensions(mine);
            for (BulkMiningCommittedResult.Period period : result.periods()) {
                for (var entry : dimensions.entrySet()) {
                    MiningStatKey key = new MiningStatKey(playerId, period.type(), period.key(),
                            entry.getKey().type(), entry.getKey().key());
                    miningTotals.merge(key, entry.getValue(), Long::sum);
                }
            }
        }
    }
    public void recordRankup(UUID playerId) { pending(playerId).rankups.increment(); }
    public void recordPrestige(UUID playerId) { pending(playerId).prestiges.increment(); }
    public void recordBooster(UUID playerId) { if (playerId != null) pending(playerId).boosters.increment(); }

    private PendingCounters pending(UUID playerId) {
        return playerPending.computeIfAbsent(playerId, ignored -> new PendingCounters());
    }

    private void loadPlayer(UUID playerId) {
        if (playerCounters.containsKey(playerId)) return;
        database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT items_sold,rankups,prestiges,boosters_used,playtime_seconds FROM rp_player_statistics WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? new PlayerCounters(result.getLong(1), result.getLong(2), result.getLong(3),
                            result.getLong(4), result.getLong(5)) : PlayerCounters.ZERO;
                }
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        }).thenAccept(value -> playerCounters.putIfAbsent(playerId, value)).exceptionally(error -> {
            plugin.getLogger().warning("Unable to load statistics for " + playerId + ": " + rootMessage(error));
            return null;
        });
        loadMiningStatsForPlayer(playerId);
    }

    private void loadMiningStatsForPlayer(UUID playerId) {
        if (!playerMiningStatistics.enabled()) return;
        if (!miningStatsLoadedPlayers.add(playerId)) return;
        database.submitIdempotent(connection -> {
            List<PeriodKey> activePeriods = activeMiningPeriods();
            StringBuilder clause = new StringBuilder("player_uuid=? AND (");
            for (int index = 0; index < activePeriods.size(); index++) {
                if (index > 0) clause.append(" OR ");
                clause.append("(period_type=? AND period_key=?)");
            }
            clause.append(')');
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT period_type,period_key,dimension_type,dimension_key,blocks FROM rp_mining_statistics WHERE "
                            + clause)) {
                int parameter = 1;
                statement.setString(parameter++, playerId.toString());
                for (PeriodKey period : activePeriods) {
                    statement.setString(parameter++, period.type());
                    statement.setString(parameter++, period.key());
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        MiningStatKey key = new MiningStatKey(playerId, result.getString(1), result.getString(2),
                                result.getString(3), result.getString(4));
                        miningTotals.merge(key, result.getLong(5), Long::sum);
                    }
                }
                return null;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        }).exceptionally(error -> {
            miningStatsLoadedPlayers.remove(playerId);
            plugin.getLogger().warning("Unable to load mining statistics for " + playerId + ": " + rootMessage(error));
            return null;
        });
    }

    private void flushAsync() { flush().exceptionally(error -> { plugin.getLogger().warning("Statistics flush failed: " + rootMessage(error)); return null; }); }

    public CompletableFuture<Void> flush() {
        Map<String, Long> mines = drain(minePending);
        Map<PlayerMineKey, Long> playerMines = drain(playerMinePending);
        Map<MiningStatKey, Long> miningStats = drain(miningPending);
        Map<UUID, PlayerDelta> players = drainPlayers();
        if (mines.isEmpty() && playerMines.isEmpty() && players.isEmpty() && miningStats.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return database.submitIdempotent(connection -> {
            try {
                boolean mysql = database.storageType() == site.mcrelicworld.relicprison.config.StorageConfig.Type.MYSQL;
                String mineSql = mysql
                        ? "INSERT INTO rp_mine_statistics(mine_id,lifetime_blocks,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE lifetime_blocks=lifetime_blocks+VALUES(lifetime_blocks),updated_at=VALUES(updated_at)"
                        : "INSERT INTO rp_mine_statistics(mine_id,lifetime_blocks,updated_at) VALUES(?,?,?) ON CONFLICT(mine_id) DO UPDATE SET lifetime_blocks=rp_mine_statistics.lifetime_blocks+excluded.lifetime_blocks,updated_at=excluded.updated_at";
                try (PreparedStatement statement = connection.prepareStatement(mineSql)) {
                    for (var entry : mines.entrySet()) { statement.setString(1, entry.getKey()); statement.setLong(2, entry.getValue()); statement.setLong(3, System.currentTimeMillis()); statement.addBatch(); }
                    statement.executeBatch();
                }
                String playerMineSql = mysql
                        ? "INSERT INTO rp_player_mine_statistics(player_uuid,mine_id,lifetime_blocks,updated_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE lifetime_blocks=lifetime_blocks+VALUES(lifetime_blocks),updated_at=VALUES(updated_at)"
                        : "INSERT INTO rp_player_mine_statistics(player_uuid,mine_id,lifetime_blocks,updated_at) VALUES(?,?,?,?) ON CONFLICT(player_uuid,mine_id) DO UPDATE SET lifetime_blocks=rp_player_mine_statistics.lifetime_blocks+excluded.lifetime_blocks,updated_at=excluded.updated_at";
                try (PreparedStatement statement = connection.prepareStatement(playerMineSql)) {
                    for (var entry : playerMines.entrySet()) { statement.setString(1, entry.getKey().playerId().toString()); statement.setString(2, entry.getKey().mineId()); statement.setLong(3, entry.getValue()); statement.setLong(4, System.currentTimeMillis()); statement.addBatch(); }
                    statement.executeBatch();
                }
                String playerSql = mysql
                        ? "INSERT INTO rp_player_statistics(player_uuid,items_sold,rankups,prestiges,boosters_used,playtime_seconds,updated_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE items_sold=items_sold+VALUES(items_sold),rankups=rankups+VALUES(rankups),prestiges=prestiges+VALUES(prestiges),boosters_used=boosters_used+VALUES(boosters_used),playtime_seconds=playtime_seconds+VALUES(playtime_seconds),updated_at=VALUES(updated_at)"
                        : "INSERT INTO rp_player_statistics(player_uuid,items_sold,rankups,prestiges,boosters_used,playtime_seconds,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET items_sold=rp_player_statistics.items_sold+excluded.items_sold,rankups=rp_player_statistics.rankups+excluded.rankups,prestiges=rp_player_statistics.prestiges+excluded.prestiges,boosters_used=rp_player_statistics.boosters_used+excluded.boosters_used,playtime_seconds=rp_player_statistics.playtime_seconds+excluded.playtime_seconds,updated_at=excluded.updated_at";
                try (PreparedStatement statement = connection.prepareStatement(playerSql)) {
                    for (var entry : players.entrySet()) {
                        PlayerDelta d=entry.getValue(); statement.setString(1, entry.getKey().toString()); statement.setLong(2,d.itemsSold()); statement.setLong(3,d.rankups()); statement.setLong(4,d.prestiges()); statement.setLong(5,d.boosters()); statement.setLong(6,d.playtime()); statement.setLong(7,System.currentTimeMillis()); statement.addBatch();
                    }
                    statement.executeBatch();
                }
                String miningSql = mysql
                        ? "INSERT INTO rp_mining_statistics(player_uuid,period_type,period_key,dimension_type,dimension_key,blocks,updated_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE blocks=blocks+VALUES(blocks),updated_at=VALUES(updated_at)"
                        : "INSERT INTO rp_mining_statistics(player_uuid,period_type,period_key,dimension_type,dimension_key,blocks,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(player_uuid,period_type,period_key,dimension_type,dimension_key) DO UPDATE SET blocks=rp_mining_statistics.blocks+excluded.blocks,updated_at=excluded.updated_at";
                try (PreparedStatement statement = connection.prepareStatement(miningSql)) {
                    long now = System.currentTimeMillis();
                    for (var entry : miningStats.entrySet()) {
                        MiningStatKey key = entry.getKey();
                        statement.setString(1, key.playerId().toString());
                        statement.setString(2, key.periodType());
                        statement.setString(3, key.periodKey());
                        statement.setString(4, key.dimensionType());
                        statement.setString(5, key.dimensionKey());
                        statement.setLong(6, entry.getValue());
                        statement.setLong(7, now);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return null;
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        }).thenRun(() -> {
            players.forEach((id, d) -> playerCounters.merge(id, d.toCounters(), PlayerCounters::add));
            evictInactive(System.currentTimeMillis());
        })
          .exceptionally(error -> {
              mines.forEach((id, value) -> minePending.computeIfAbsent(id, ignored -> new LongAdder()).add(value));
              playerMines.forEach((key, value) -> playerMinePending.computeIfAbsent(key, ignored -> new LongAdder()).add(value));
              miningStats.forEach((key, value) -> miningPending.computeIfAbsent(key, ignored -> new LongAdder()).add(value));
              players.forEach((id, d) -> pending(id).add(d));
              throw new java.util.concurrent.CompletionException(error);
          });
    }

    private static <K> Map<K, Long> drain(Map<K, LongAdder> source) {
        Map<K, Long> result = new java.util.HashMap<>();
        source.forEach((key, adder) -> { long value=adder.sumThenReset(); if(value>0) result.put(key,value); });
        source.entrySet().removeIf(entry -> entry.getValue().sum() == 0L);
        return result;
    }

    private Map<UUID, PlayerDelta> drainPlayers() {
        Map<UUID, PlayerDelta> result = new java.util.HashMap<>();
        playerPending.forEach((id, pending) -> { PlayerDelta delta=pending.drain(); if(!delta.empty()) result.put(id,delta); });
        playerPending.entrySet().removeIf(entry -> entry.getValue().empty());
        return result;
    }

    @Override public long lifetimeBlocks(UUID playerId) {
        return plugin.playerProfiles().cachedProfile(playerId).map(PlayerProfile::lifetimeBlocks).orElse(0L);
    }
    @Override public long mineBlocks(String mineId) {
        if (!mineAnalytics.enabled()) return 0L;
        return mineTotals.getOrDefault(mineId.toLowerCase(Locale.ROOT), 0L);
    }
    @Override public long itemsSold(UUID id) { return counters(id).itemsSold(); }
    @Override public long rankups(UUID id) { return counters(id).rankups(); }
    @Override public long prestiges(UUID id) { return counters(id).prestiges(); }
    @Override public long boostersUsed(UUID id) { return counters(id).boosters(); }
    @Override public long playtimeSeconds(UUID id) {
        long current = sessions.containsKey(id) ? Math.max(0L,(System.currentTimeMillis()-sessions.get(id))/1000L) : 0L;
        return counters(id).playtime()+current;
    }
    private PlayerCounters counters(UUID id) {
        PlayerCounters base=playerCounters.getOrDefault(id,PlayerCounters.ZERO);
        PendingCounters pending=playerPending.get(id);
        return pending==null ? base : base.add(pending.snapshot().toCounters());
    }

    public long miningStat(UUID playerId, String period, String dimensionType, String dimensionKey) {
        if (playerId == null || dimensionType == null || dimensionKey == null) return 0L;
        if (!playerMiningStatistics.enabled()) return 0L;
        try {
            PeriodReference reference = periodReference(period);
            String type = dimensionType.toLowerCase(Locale.ROOT);
            String key = dimensionKey.toLowerCase(Locale.ROOT);
            return miningTotals.getOrDefault(new MiningStatKey(playerId, reference.type(), reference.id(), type, key), 0L);
        } catch (IllegalArgumentException ex) {
            return 0L;
        }
    }

    public Optional<List<LeaderboardEntry>> cachedLeaderboard(String metric, String period, int limit) {
        try {
            LeaderboardRequest request = request(metric, period, limit);
            SnapshotCache cached = leaderboardCache.get(request);
            long now = System.currentTimeMillis();
            if (cached != null && cached.expiresAt() > now) return Optional.of(cached.entries());
            refresh(request);
            return cached == null ? Optional.empty() : Optional.of(cached.entries());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Optional<Integer> cachedPosition(UUID playerId, String metric, String period) {
        if (playerId == null) return Optional.empty();
        return cachedLeaderboard(metric, period, plugin.config().snapshot().leaderboards().snapshotSize())
                .flatMap(entries -> entries.stream()
                        .filter(entry -> entry.playerId().equals(playerId))
                        .map(LeaderboardEntry::position)
                        .findFirst());
    }

    public LeaderboardCatalog leaderboardCatalog() { return leaderboardCatalog; }

    public int pendingWriteBuckets() {
        return minePending.size() + playerMinePending.size() + miningPending.size() + playerPending.size();
    }

    public int playerStatisticCacheSize() {
        return playerCounters.size() + miningStatsLoadedPlayers.size();
    }

    public int dirtyPlayerStatisticEntries() {
        java.util.Set<UUID> dirty = new java.util.HashSet<>(playerPending.keySet());
        playerMinePending.keySet().forEach(key -> dirty.add(key.playerId()));
        miningPending.keySet().forEach(key -> dirty.add(key.playerId()));
        return dirty.size();
    }

    public long failedStatisticEvictions() {
        return failedEvictionFlushes.sum();
    }

    public int cachedLeaderboardSnapshots() {
        return leaderboardCache.size();
    }

    public void reloadLeaderboards() throws Exception {
        applyLeaderboards(LeaderboardCatalog.load(plugin.getDataFolder()));
    }

    public void applyLeaderboards(LeaderboardCatalog next) {
        leaderboardCatalog = next;
        leaderboardCache.clear();
        refreshConfiguredLeaderboards();
    }

    @Override public CompletableFuture<List<LeaderboardEntry>> leaderboard(String metric, String period, int limit) {
        LeaderboardRequest request = request(metric, period, limit);
        SnapshotCache cached = leaderboardCache.get(request);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) return CompletableFuture.completedFuture(cached.entries());
        return refresh(request);
    }

    private CompletableFuture<List<LeaderboardEntry>> refresh(LeaderboardRequest request) {
        return leaderboardLoads.computeIfAbsent(request, ignored -> flush().thenCompose(flushed -> database.submitIdempotent(connection -> {
            List<LeaderboardEntry> result=new ArrayList<>();
            Query query=query(request);
            try (PreparedStatement statement=connection.prepareStatement(query.sql()+" LIMIT ?")) {
                int index = 1;
                for (Object parameter : query.parameters()) {
                    if (parameter instanceof String value) statement.setString(index++, value);
                    else if (parameter instanceof Integer value) statement.setInt(index++, value);
                    else statement.setObject(index++, parameter);
                }
                statement.setInt(index,safeLimit(request.limit()));
                try(ResultSet rs=statement.executeQuery()) { int pos=1; while(rs.next()) result.add(new LeaderboardEntry(pos++,UUID.fromString(rs.getString("uuid")),rs.getString("name"),new BigDecimal(rs.getString("value")))); }
                List<LeaderboardEntry> entries = List.copyOf(result);
                leaderboardCache.put(request, new SnapshotCache(entries, System.currentTimeMillis()
                        + plugin.config().snapshot().leaderboards().refreshSeconds() * 1000L));
                return List.copyOf(result);
            } catch(SQLException ex){ throw new DatabaseManager.DatabaseException(ex); }
        })).whenComplete((entries, error) -> leaderboardLoads.remove(request)));
    }

    private void evictInactive(long now) {
        List<Map.Entry<UUID, Long>> candidates = inactiveSince.entrySet().stream()
                .filter(entry -> !sessions.containsKey(entry.getKey()))
                .filter(entry -> !dirty(entry.getKey()))
                .sorted(Map.Entry.comparingByValue())
                .toList();
        for (Map.Entry<UUID, Long> entry : candidates) {
            boolean expired = now - entry.getValue() >= PLAYER_COUNTER_IDLE_MILLIS;
            boolean pressure = playerCounters.size() > MAX_PLAYER_COUNTER_CACHE;
            if (!expired && !pressure) break;
            UUID playerId = entry.getKey();
            playerCounters.remove(playerId);
            miningStatsLoadedPlayers.remove(playerId);
            miningTotals.keySet().removeIf(key -> key.playerId().equals(playerId));
            inactiveSince.remove(playerId);
        }
    }

    private boolean dirty(UUID playerId) {
        if (playerPending.containsKey(playerId)) return true;
        for (PlayerMineKey key : playerMinePending.keySet()) if (key.playerId().equals(playerId)) return true;
        for (MiningStatKey key : miningPending.keySet()) if (key.playerId().equals(playerId)) return true;
        return false;
    }

    private Query query(LeaderboardRequest request) {
        String metric = request.metric();
        String periodType = request.periodType();
        if(metric.equals("blocks")) {
            if (periodType.equals("lifetime")) {
                return new Query("SELECT uuid,COALESCE(NULLIF(last_name,''),'unknown') AS name,lifetime_blocks AS value "
                        + "FROM rp_player_profiles ORDER BY lifetime_blocks DESC,LOWER(last_name) ASC,uuid ASC",
                        List.of());
            }
            return new Query("SELECT p.uuid,COALESCE(NULLIF(p.last_name,''),'unknown') AS name,s.blocks AS value "
                    + "FROM rp_mining_statistics s JOIN rp_player_profiles p ON p.uuid=s.player_uuid "
                    + "WHERE s.period_type=? AND s.period_key=? AND s.dimension_type='total' "
                    + "AND s.dimension_key='blocks' ORDER BY s.blocks DESC,LOWER(p.last_name) ASC,p.uuid ASC",
                    List.of(periodType, request.periodId()));
        }
        if(metric.equals("rank")) return new Query("SELECT uuid,COALESCE(NULLIF(last_name,''),'unknown') AS name,"
                + rankCase() + " AS value FROM rp_player_profiles ORDER BY value DESC,lifetime_blocks DESC,"
                + "LOWER(last_name) ASC,uuid ASC", List.of());
        if(metric.equals("prestige")) return new Query("SELECT uuid,COALESCE(NULLIF(last_name,''),'unknown') AS name,"
                + prestigeCase() + " AS value FROM rp_player_profiles ORDER BY value DESC,lifetime_blocks DESC,"
                + "LOWER(last_name) ASC,uuid ASC", List.of());
        if(metric.equals("money")) return new Query("SELECT uuid,COALESCE(NULLIF(last_name,''),'unknown') AS name,"
                + "CAST(money_earned AS DECIMAL(65,2)) AS value FROM rp_player_profiles "
                + "ORDER BY CAST(money_earned AS DECIMAL(65,2)) DESC,LOWER(last_name) ASC,uuid ASC", List.of());
        String column=switch(metric){ case "items_sold"->"items_sold"; case "rankups"->"rankups"; case "prestiges"->"prestiges"; case "boosters"->"boosters_used"; case "playtime"->"playtime_seconds"; default->throw new IllegalArgumentException("Unknown leaderboard metric: "+metric); };
        return new Query("SELECT p.uuid,COALESCE(NULLIF(p.last_name,''),'unknown') AS name,COALESCE(s."+column+",0) AS value "
                + "FROM rp_player_profiles p LEFT JOIN rp_player_statistics s ON p.uuid=s.player_uuid "
                + "ORDER BY value DESC,LOWER(p.last_name) ASC,p.uuid ASC", List.of());
    }

    private LeaderboardRequest request(String metric, String period, int limit) {
        String normalizedMetric = LeaderboardBoard.normalizeMetric(metric);
        PeriodReference reference = periodReference(period);
        return new LeaderboardRequest(normalizedMetric, reference.type(), reference.id(), safeLimit(limit));
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(plugin.config().snapshot().leaderboards().snapshotSize(), limit));
    }

    private PeriodReference periodReference(String period) {
        String value = period == null || period.isBlank() ? "lifetime" : period.trim().toLowerCase(Locale.ROOT);
        if (value.contains(":")) {
            String[] split = value.split(":", 2);
            return new PeriodReference(LeaderboardBoard.normalizePeriod(split[0]), split[1]);
        }
        String type = LeaderboardBoard.normalizePeriod(value);
        return new PeriodReference(type, LeaderboardPeriods.currentPeriodId(type, System.currentTimeMillis(),
                plugin.config().snapshot().timezone(), plugin.config().snapshot().leaderboards()));
    }

    private List<PeriodKey> activeMiningPeriods() {
        PeriodKeys periods = periods(System.currentTimeMillis());
        List<PeriodKey> active = new ArrayList<>();
        active.add(new PeriodKey("daily", periods.daily()));
        active.add(new PeriodKey("weekly", periods.weekly()));
        active.add(new PeriodKey("monthly", periods.monthly()));
        active.add(new PeriodKey("lifetime", "lifetime"));
        if (plugin.config().snapshot().leaderboards().seasonalEnabled()) {
            long now = System.currentTimeMillis();
            Instant instant = Instant.ofEpochMilli(now);
            if (!instant.isBefore(plugin.config().snapshot().leaderboards().seasonStart())
                    && instant.isBefore(plugin.config().snapshot().leaderboards().seasonEnd())) {
                active.add(new PeriodKey("season", plugin.config().snapshot().leaderboards().seasonId()));
            }
        }
        return List.copyOf(active);
    }

    private String rankCase() {
        StringBuilder builder = new StringBuilder("CASE current_rank ");
        List<site.mcrelicworld.relicprison.progression.RankDefinition> ranks = plugin.rankService().definitions();
        for (int index = 0; index < ranks.size(); index++) {
            builder.append("WHEN '").append(escape(ranks.get(index).id())).append("' THEN ").append(index);
        }
        return builder.append(" ELSE -1 END").toString();
    }

    private String prestigeCase() {
        StringBuilder builder = new StringBuilder("CASE current_prestige ");
        List<site.mcrelicworld.relicprison.progression.PrestigeDefinition> prestiges =
                plugin.prestigeService().definitions();
        for (int index = 0; index < prestiges.size(); index++) {
            builder.append("WHEN '").append(escape(prestiges.get(index).id())).append("' THEN ").append(index + 1);
        }
        return builder.append(" ELSE 0 END").toString();
    }

    private void startLeaderboardTask() {
        if (leaderboardTask != -1) Bukkit.getScheduler().cancelTask(leaderboardTask);
        long ticks = Math.max(20L, plugin.config().snapshot().leaderboards().refreshSeconds() * 20L);
        leaderboardTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::refreshConfiguredLeaderboards,
                40L, ticks);
    }

    private void refreshConfiguredLeaderboards() {
        if (!plugin.config().snapshot().features().playerLeaderboards()) return;
        int limit = plugin.config().snapshot().leaderboards().snapshotSize();
        for (LeaderboardBoard board : leaderboardCatalog.boards()) {
            try {
                refresh(request(board.metric(), board.period(), limit)).exceptionally(error -> {
                    plugin.getLogger().warning("Leaderboard refresh failed for " + board.id() + ": " + rootMessage(error));
                    return null;
                });
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping invalid leaderboard " + board.id() + ": " + ex.getMessage());
            }
        }
    }

    private static String escape(String value) { return value.replace("'", "''"); }

    private static String rootMessage(Throwable error){ Throwable current=error; while(current.getCause()!=null) current=current.getCause(); return String.valueOf(current.getMessage()); }
    private PeriodKeys periods(long epochMillis) {
        LocalDate date = Instant.ofEpochMilli(epochMillis).atZone(plugin.config().snapshot().timezone()).toLocalDate();
        WeekFields fields = WeekFields.ISO;
        String daily = date.toString();
        String weekly = date.get(fields.weekBasedYear()) + "-W" + String.format("%02d", date.get(fields.weekOfWeekBasedYear()));
        String monthly = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
        return new PeriodKeys(daily, weekly, monthly);
    }
    private record Query(String sql,List<Object> parameters){}
    private record PlayerMineKey(UUID playerId,String mineId){}
    private record MiningStatKey(UUID playerId,String periodType,String periodKey,String dimensionType,String dimensionKey){}
    private record PeriodKey(String type,String key){}
    private record PeriodKeys(String daily,String weekly,String monthly){}
    private record PeriodReference(String type,String id){}
    private record LeaderboardRequest(String metric,String periodType,String periodId,int limit){}
    private record SnapshotCache(List<LeaderboardEntry> entries,long expiresAt){}
    private record PlayerCounters(long itemsSold,long rankups,long prestiges,long boosters,long playtime){
        static final PlayerCounters ZERO=new PlayerCounters(0,0,0,0,0);
        PlayerCounters add(PlayerCounters o){return new PlayerCounters(itemsSold+o.itemsSold,rankups+o.rankups,prestiges+o.prestiges,boosters+o.boosters,playtime+o.playtime);}
    }
    private record PlayerDelta(long itemsSold,long rankups,long prestiges,long boosters,long playtime){ boolean empty(){return itemsSold==0&&rankups==0&&prestiges==0&&boosters==0&&playtime==0;} PlayerCounters toCounters(){return new PlayerCounters(itemsSold,rankups,prestiges,boosters,playtime);} }
    private static final class PendingCounters{
        final LongAdder itemsSold=new LongAdder(),rankups=new LongAdder(),prestiges=new LongAdder(),boosters=new LongAdder(),playtime=new LongAdder();
        PlayerDelta drain(){return new PlayerDelta(itemsSold.sumThenReset(),rankups.sumThenReset(),prestiges.sumThenReset(),boosters.sumThenReset(),playtime.sumThenReset());}
        PlayerDelta snapshot(){return new PlayerDelta(itemsSold.sum(),rankups.sum(),prestiges.sum(),boosters.sum(),playtime.sum());}
        boolean empty(){return snapshot().empty();}
        void add(PlayerDelta d){itemsSold.add(d.itemsSold);rankups.add(d.rankups);prestiges.add(d.prestiges);boosters.add(d.boosters);playtime.add(d.playtime);}
    }
}
