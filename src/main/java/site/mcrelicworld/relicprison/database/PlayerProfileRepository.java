package site.mcrelicworld.relicprison.database;

import site.mcrelicworld.relicprison.api.PlayerDataService;
import site.mcrelicworld.relicprison.api.model.PlayerProfileView;
import site.mcrelicworld.relicprison.mining.BulkMiningCommittedResult;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerProfileRepository implements PlayerDataService {
    private final DatabaseManager database;
    private final ZoneId timezone;
    private final int loadTimeoutSeconds;
    private volatile String startingRank;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final Map<UUID, LoadAttempt> inFlightLoads = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerLoadState> loadStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeSessions = new ConcurrentHashMap<>();
    private final AtomicLong sessionSequence = new AtomicLong();
    private final AtomicLong failedSaves = new AtomicLong();

    public PlayerProfileRepository(DatabaseManager database, ZoneId timezone, String startingRank) {
        this(database, timezone, startingRank, 15);
    }

    public PlayerProfileRepository(DatabaseManager database, ZoneId timezone, String startingRank, int loadTimeoutSeconds) {
        this.database = database;
        this.timezone = timezone;
        this.loadTimeoutSeconds = Math.max(1, loadTimeoutSeconds);
        this.startingRank = java.util.Objects.requireNonNull(startingRank).toLowerCase(java.util.Locale.ROOT);
    }

    public void updateStartingRank(String value) {
        startingRank = java.util.Objects.requireNonNull(value).toLowerCase(java.util.Locale.ROOT);
    }

    @Override public Optional<PlayerProfileView> cached(UUID playerId) { return Optional.ofNullable(cache.get(playerId)); }
    public Optional<PlayerProfile> cachedProfile(UUID playerId) { return Optional.ofNullable(cache.get(playerId)); }
    public PlayerLoadState loadState(UUID playerId) { return loadStates.getOrDefault(playerId, PlayerLoadState.NOT_LOADED); }

    public long startSession(UUID playerId) {
        long session = sessionSequence.incrementAndGet();
        activeSessions.put(playerId, session);
        loadStates.put(playerId, PlayerLoadState.LOADING);
        return session;
    }

    @Override public CompletableFuture<PlayerProfileView> load(UUID playerId, String playerName) {
        long session = activeSessions.computeIfAbsent(playerId, ignored -> sessionSequence.incrementAndGet());
        PlayerProfile existing = cache.get(playerId);
        if (existing != null) {
            existing.touch(playerName, System.currentTimeMillis());
            existing.rollPeriods(periodKeys(System.currentTimeMillis()));
            loadStates.put(playerId, PlayerLoadState.READY);
            return CompletableFuture.completedFuture(existing);
        }
        loadStates.put(playerId, PlayerLoadState.LOADING);
        return loadForSession(playerId, playerName, session);
    }

    private CompletableFuture<PlayerProfileView> loadForSession(UUID playerId, String playerName, long session) {
        while (true) {
            LoadAttempt current = inFlightLoads.get(playerId);
            if (current != null) {
                if (current.session() == session) return current.future();
                return current.future().handle((ignored, error) -> null).thenCompose(ignored -> {
                    if (!isCurrentSession(playerId, session)) {
                        return CompletableFuture.failedFuture(new StalePlayerLoadException(playerId));
                    }
                    PlayerProfile cachedProfile = cache.get(playerId);
                    if (cachedProfile != null) {
                        loadStates.put(playerId, PlayerLoadState.READY);
                        return CompletableFuture.completedFuture(cachedProfile);
                    }
                    return loadForSession(playerId, playerName, session);
                });
            }

            CompletableFuture<PlayerProfileView> promise = new CompletableFuture<>();
            LoadAttempt attempt = new LoadAttempt(session, promise);
            if (inFlightLoads.putIfAbsent(playerId, attempt) != null) continue;
            beginLoad(playerId, playerName, attempt);
            return promise;
        }
    }

    private void beginLoad(UUID playerId, String playerName, LoadAttempt attempt) {
        loadUncached(playerId, playerName)
                .orTimeout(loadTimeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((profile, error) -> {
                    try {
                        if (error != null) {
                            Throwable cause = root(error);
                            if (isCurrentSession(playerId, attempt.session())) markLoadFailure(playerId, cause);
                            attempt.future().completeExceptionally(cause);
                            return;
                        }
                        if (!isCurrentSession(playerId, attempt.session())) {
                            attempt.future().completeExceptionally(new StalePlayerLoadException(playerId));
                            return;
                        }
                        PlayerProfile winner = cache.putIfAbsent(playerId, profile);
                        PlayerProfile active = winner == null ? profile : winner;
                        loadStates.put(playerId, PlayerLoadState.READY);
                        attempt.future().complete(active);
                    } finally {
                        inFlightLoads.remove(playerId, attempt);
                    }
                });
    }

    private boolean isCurrentSession(UUID playerId, long session) {
        return Long.valueOf(session).equals(activeSessions.get(playerId));
    }

    private CompletableFuture<PlayerProfile> loadUncached(UUID playerId, String playerName) {
        return database.submitIdempotent(connection -> {
            String sql = "SELECT * FROM rp_player_profiles WHERE uuid=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    long now = System.currentTimeMillis();
                    PlayerProfile profile = result.next()
                            ? fromResult(result)
                            : PlayerProfile.create(playerId, playerName, startingRank, now, periodKeys(now));
                    profile.touch(playerName, now);
                    profile.rollPeriods(periodKeys(now));
                    return profile;
                }
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        }).thenCompose(profile -> profile.dirty()
                ? save(profile).thenApply(ignored -> profile)
                : CompletableFuture.completedFuture(profile));
    }

    public CompletableFuture<Void> save(PlayerProfile profile) {
        return database.<Void>submitIdempotent(connection -> {
            long revision = profile.revision();
            String sql = "INSERT INTO rp_player_profiles (uuid,last_name,current_rank,current_prestige,first_join,last_join," +
                    "autosell,autopickup,autosmelt,autoblock,lifetime_blocks,daily_blocks,weekly_blocks,monthly_blocks," +
                    "daily_period,weekly_period,monthly_period,money_earned,data_version,updated_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " + upsertClause();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int i = 1;
                statement.setString(i++, profile.uuid().toString());
                statement.setString(i++, profile.lastName());
                statement.setString(i++, profile.currentRank());
                statement.setString(i++, profile.currentPrestige());
                statement.setLong(i++, profile.firstJoin());
                statement.setLong(i++, profile.lastJoin());
                statement.setBoolean(i++, profile.autoSell());
                statement.setBoolean(i++, profile.autoPickup());
                statement.setBoolean(i++, profile.autoSmelt());
                statement.setBoolean(i++, profile.autoBlock());
                statement.setLong(i++, profile.lifetimeBlocks());
                statement.setLong(i++, profile.dailyBlocks());
                statement.setLong(i++, profile.weeklyBlocks());
                statement.setLong(i++, profile.monthlyBlocks());
                statement.setString(i++, profile.dailyPeriod());
                statement.setString(i++, profile.weeklyPeriod());
                statement.setString(i++, profile.monthlyPeriod());
                statement.setString(i++, profile.moneyEarned().toPlainString());
                statement.setInt(i++, profile.dataVersion());
                statement.setLong(i, System.currentTimeMillis());
                statement.executeUpdate();
                profile.markSaved(revision);
                return null;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) failedSaves.incrementAndGet();
        });
    }

    private String upsertClause() {
        if (database.storageType() == site.mcrelicworld.relicprison.config.StorageConfig.Type.MYSQL) {
            return "ON DUPLICATE KEY UPDATE " +
                    "last_name=VALUES(last_name),current_rank=VALUES(current_rank),current_prestige=VALUES(current_prestige)," +
                    "first_join=VALUES(first_join),last_join=VALUES(last_join),autosell=VALUES(autosell)," +
                    "autopickup=VALUES(autopickup),autosmelt=VALUES(autosmelt),autoblock=VALUES(autoblock)," +
                    "lifetime_blocks=VALUES(lifetime_blocks),daily_blocks=VALUES(daily_blocks),weekly_blocks=VALUES(weekly_blocks)," +
                    "monthly_blocks=VALUES(monthly_blocks),daily_period=VALUES(daily_period),weekly_period=VALUES(weekly_period)," +
                    "monthly_period=VALUES(monthly_period),money_earned=VALUES(money_earned),data_version=VALUES(data_version)," +
                    "updated_at=VALUES(updated_at)";
        }
        return "ON CONFLICT(uuid) DO UPDATE SET " +
                "last_name=excluded.last_name,current_rank=excluded.current_rank,current_prestige=excluded.current_prestige," +
                "first_join=excluded.first_join,last_join=excluded.last_join,autosell=excluded.autosell," +
                "autopickup=excluded.autopickup,autosmelt=excluded.autosmelt,autoblock=excluded.autoblock," +
                "lifetime_blocks=excluded.lifetime_blocks,daily_blocks=excluded.daily_blocks,weekly_blocks=excluded.weekly_blocks," +
                "monthly_blocks=excluded.monthly_blocks,daily_period=excluded.daily_period,weekly_period=excluded.weekly_period," +
                "monthly_period=excluded.monthly_period,money_earned=excluded.money_earned,data_version=excluded.data_version," +
                "updated_at=excluded.updated_at";
    }

    public CompletableFuture<Void> flushDirty() {
        CompletableFuture<?>[] futures = cache.values().stream()
                .filter(PlayerProfile::dirty)
                .map(this::save)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /** Saves before eviction. A failed save keeps the profile cached for a later retry. */
    public CompletableFuture<Void> unload(UUID playerId) {
        Long endedSession = activeSessions.remove(playerId);
        PlayerProfile profile = cache.get(playerId);
        if (profile == null) {
            LoadAttempt loading = inFlightLoads.get(playerId);
            if (loading == null || endedSession == null || loading.session() != endedSession) {
                if (!activeSessions.containsKey(playerId)) loadStates.put(playerId, PlayerLoadState.NOT_LOADED);
                return CompletableFuture.completedFuture(null);
            }
            return loading.future().handle((ignored, error) -> null)
                    .thenCompose(ignored -> unloadLoadedProfile(playerId, endedSession));
        }
        return unloadLoadedProfile(playerId, endedSession);
    }

    private CompletableFuture<Void> unloadLoadedProfile(UUID playerId, Long endedSession) {
        PlayerProfile profile = cache.get(playerId);
        if (profile == null) {
            if (!activeSessions.containsKey(playerId)) loadStates.put(playerId, PlayerLoadState.NOT_LOADED);
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> save = profile.dirty() ? save(profile) : CompletableFuture.completedFuture(null);
        return save.thenRun(() -> {
            Long currentSession = activeSessions.get(playerId);
            if (currentSession != null && !currentSession.equals(endedSession)) return;
            if (currentSession == null) {
                cache.remove(playerId, profile);
                loadStates.put(playerId, PlayerLoadState.NOT_LOADED);
            }
        });
    }

    public void markLoadFailure(UUID playerId, Throwable error) {
        Throwable cause = root(error);
        if (cause instanceof StalePlayerLoadException) {
            if (activeSessions.containsKey(playerId)) loadStates.put(playerId, PlayerLoadState.LOADING);
            else loadStates.put(playerId, PlayerLoadState.NOT_LOADED);
        } else if (cause instanceof TimeoutException) {
            loadStates.put(playerId, PlayerLoadState.TIMED_OUT);
        } else {
            loadStates.put(playerId, PlayerLoadState.FAILED);
        }
    }

    public void recordBlocks(UUID playerId, long amount) {
        PlayerProfile profile = cache.get(playerId);
        if (profile == null || amount <= 0) return;
        profile.rollPeriods(periodKeys(System.currentTimeMillis()));
        profile.addBlocks(amount);
    }

    public void reflectCommittedBulk(UUID playerId, BulkMiningCommittedResult result) {
        PlayerProfile profile = cache.get(playerId);
        if (profile == null || result.totalBlocks() <= 0) return;
        Map<String, String> periods = new java.util.HashMap<>();
        for (BulkMiningCommittedResult.Period period : result.periods()) periods.put(period.type(), period.key());
        profile.rollPeriods(new PlayerProfile.PeriodKeys(
                periods.getOrDefault("daily", profile.dailyPeriod()),
                periods.getOrDefault("weekly", profile.weeklyPeriod()),
                periods.getOrDefault("monthly", profile.monthlyPeriod())));
        profile.addBlocks(result.totalBlocks());
        profile.addMoneyEarned(result.moneyEarned());
    }

    public Collection<PlayerProfile> cachedProfiles() { return List.copyOf(cache.values()); }
    public int dirtyProfileCount() { return (int) cache.values().stream().filter(PlayerProfile::dirty).count(); }
    public long failedSaveCount() { return failedSaves.get(); }
    public int loadingProfileCount() { return countState(PlayerLoadState.LOADING); }
    public int timedOutProfileLoadCount() { return countState(PlayerLoadState.TIMED_OUT); }
    public int failedProfileLoadCount() { return countState(PlayerLoadState.FAILED); }

    private int countState(PlayerLoadState state) {
        int count = 0;
        for (PlayerLoadState value : loadStates.values()) {
            if (value == state) count++;
        }
        return count;
    }

    private PlayerProfile.PeriodKeys periodKeys(long epochMillis) {
        LocalDate date = Instant.ofEpochMilli(epochMillis).atZone(timezone).toLocalDate();
        WeekFields fields = WeekFields.ISO;
        String daily = date.toString();
        String weekly = date.get(fields.weekBasedYear()) + "-W" + String.format("%02d", date.get(fields.weekOfWeekBasedYear()));
        String monthly = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
        return new PlayerProfile.PeriodKeys(daily, weekly, monthly);
    }

    private static PlayerProfile fromResult(ResultSet result) throws SQLException {
        return new PlayerProfile(
                UUID.fromString(result.getString("uuid")), result.getString("last_name"), result.getString("current_rank"),
                result.getString("current_prestige"), result.getLong("first_join"), result.getLong("last_join"),
                result.getBoolean("autosell"), result.getBoolean("autopickup"), result.getBoolean("autosmelt"),
                result.getBoolean("autoblock"), result.getLong("lifetime_blocks"), result.getLong("daily_blocks"),
                result.getLong("weekly_blocks"), result.getLong("monthly_blocks"), result.getString("daily_period"),
                result.getString("weekly_period"), result.getString("monthly_period"),
                new BigDecimal(result.getString("money_earned"))
        );
    }

    private static Throwable root(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    private record LoadAttempt(long session, CompletableFuture<PlayerProfileView> future) { }

    public static final class StalePlayerLoadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        StalePlayerLoadException(UUID playerId) {
            super("Ignoring stale player profile load for " + playerId);
        }
    }
}
