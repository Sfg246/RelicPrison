package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.LeaderboardEntry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Player, mine, and leaderboard statistics. */
public interface StatisticsService {
    /** Thread-safe. Returns cached lifetime blocks for a loaded profile. */
    long lifetimeBlocks(UUID playerId);
    /** Thread-safe. Returns cached mine block totals. */
    long mineBlocks(String mineId);
    /** Thread-safe. Returns cached item sold counters. */
    long itemsSold(UUID playerId);
    /** Thread-safe. Returns cached rankup counters. */
    long rankups(UUID playerId);
    /** Thread-safe. Returns cached prestige counters. */
    long prestiges(UUID playerId);
    /** Thread-safe. Returns cached booster-use counters. */
    long boostersUsed(UUID playerId);
    /** Thread-safe. Returns cached playtime plus the active session. */
    long playtimeSeconds(UUID playerId);
    /** Asynchronous. Returns a future and must not be joined on the main thread. */
    CompletableFuture<List<LeaderboardEntry>> leaderboard(String metric, String period, int limit);
}
