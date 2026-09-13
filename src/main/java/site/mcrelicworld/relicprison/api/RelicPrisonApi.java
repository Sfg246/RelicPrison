package site.mcrelicworld.relicprison.api;

public interface RelicPrisonApi {
    /** Thread-safe. Returns the mine catalog service. */
    MineService mines();
    /** Main-thread only for mutating reset requests; read methods are thread-safe. */
    MineResetService resets();
    /** Thread-safe. Returns cached access decisions from loaded player profiles. */
    MineAccessService mineAccess();
    /** Thread-safe. Returns the immutable rank catalog service. */
    RankService ranks();
    /** Thread-safe. Returns the immutable prestige catalog service. */
    PrestigeService prestiges();
    /** Main-thread only for rankup/prestige starts; repair returns a future. */
    ProgressionService progression();
    /** Thread-safe. Profile loads return futures and must not be joined on the main thread. */
    PlayerDataService players();
    /** Main-thread only for inventory operations; returned futures must not be joined on the main thread. */
    SellService selling();
    /** Thread-safe. Returns cached multiplier values. */
    MultiplierService multipliers();
    /** Thread-safe for reads; activation futures must not be joined on the main thread. */
    BoosterService boosters();
    /** Thread-safe. Returns mining pipeline counters. */
    MiningService mining();
    /** Thread-safe for cached counters; leaderboard futures must not be joined on the main thread. */
    StatisticsService statistics();
    /** Asynchronous. Returned backup futures must not be joined on the main thread. */
    BackupService backups();
    /** Asynchronous for exports; snapshots are immutable. */
    DiagnosticService diagnostics();
    /** Thread-safe. Formats numbers using the current immutable formatting snapshot. */
    NumberFormatService numbers();
    /** Thread-safe. Returns the running RelicPrison version string. */
    String version();
}
