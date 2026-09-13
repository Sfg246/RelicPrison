package site.mcrelicworld.relicprison.database;

import org.bukkit.plugin.java.JavaPlugin;
import site.mcrelicworld.relicprison.config.StorageConfig;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class DatabaseManager implements AutoCloseable {
    public static final int SUPPORTED_SCHEMA_VERSION = 17;

    private final Logger logger;
    private final StorageConfig config;
    private final ThreadPoolExecutor executor;
    private final CopyOnWriteArrayList<Runnable> reconnectListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean transientFailureObserved = new AtomicBoolean();
    private volatile SimpleConnectionPool pool;

    public DatabaseManager(JavaPlugin plugin, StorageConfig config) {
        this(Objects.requireNonNull(plugin, "plugin").getLogger(), config);
    }

    DatabaseManager(Logger logger, StorageConfig config) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.config = Objects.requireNonNull(config, "config");
        this.executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(config.queueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "RelicPrison-Database");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((ignored, error) ->
                            this.logger.severe("Uncaught database worker error: " + error.getMessage()));
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void initialize() throws Exception {
        initializeBlocking();
    }

    public CompletableFuture<Void> initializeAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    initializeBlocking();
                    future.complete(null);
                } catch (Throwable error) {
                    future.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(new DatabaseQueueFullException(config.queueCapacity(), ex));
        }
        return future.orTimeout(30, TimeUnit.SECONDS);
    }

    private void initializeBlocking() throws Exception {
        if (config.type() == StorageConfig.Type.SQLITE) {
            Files.createDirectories(config.sqliteFile().getParent());
            Class.forName("org.sqlite.JDBC");
        } else {
            Class.forName("com.mysql.cj.jdbc.Driver");
        }
        pool = new SimpleConnectionPool(config);
        withConnectionSync(connection -> {
            configureConnection(connection);
            migrate(connection);
            return null;
        });
    }

    private void configureConnection(Connection connection) throws SQLException {
        if (config.type() != StorageConfig.Type.SQLITE) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=10000");
            statement.execute("PRAGMA foreign_keys=ON");
        }
    }

    private void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_schema_version (version INTEGER PRIMARY KEY, applied_at BIGINT NOT NULL)");
        }
        int currentVersion = schemaVersion(connection);
        if (currentVersion > SUPPORTED_SCHEMA_VERSION) {
            throw new SQLException("Database schema version " + currentVersion + " is newer than this plugin supports");
        }
        if (currentVersion < 1) {
            applyMigrationOne(connection);
            currentVersion = 1;
        }
        if (currentVersion < 2) {
            applyMigrationTwo(connection);
            currentVersion = 2;
        }
        if (currentVersion < 3) {
            applyMigrationThree(connection);
            currentVersion = 3;
        }
        if (currentVersion < 4) {
            applyMigrationFour(connection);
            currentVersion = 4;
        }
        if (currentVersion < 5) {
            applyMigrationFive(connection);
            currentVersion = 5;
        }
        if (currentVersion < 6) {
            applyMigrationSix(connection);
            currentVersion = 6;
        }
        if (currentVersion < 7) {
            applyMigrationSeven(connection);
            currentVersion = 7;
        }
        if (currentVersion < 8) {
            applyMigrationEight(connection);
            currentVersion = 8;
        }
        if (currentVersion < 9) {
            applyMigrationNine(connection);
            currentVersion = 9;
        }
        if (currentVersion < 10) {
            applyMigrationTen(connection);
            currentVersion = 10;
        }
        if (currentVersion < 11) {
            applyMigrationEleven(connection);
            currentVersion = 11;
        }
        if (currentVersion < 12) {
            applyMigrationTwelve(connection);
            currentVersion = 12;
        }
        if (currentVersion < 13) {
            applyMigrationThirteen(connection);
            currentVersion = 13;
        }
        if (currentVersion < 14) {
            applyMigrationFourteen(connection);
            currentVersion = 14;
        }
        if (currentVersion < 15) {
            applyMigrationFifteen(connection);
            currentVersion = 15;
        }
        if (currentVersion < 16) {
            applyMigrationSixteen(connection);
            currentVersion = 16;
        }
        if (currentVersion < 17) applyMigrationSeventeen(connection);
    }

    private static int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT MAX(version) FROM rp_schema_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static void applyMigrationOne(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_player_profiles (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "last_name VARCHAR(32) NOT NULL," +
                    "current_rank VARCHAR(32) NOT NULL," +
                    "current_prestige VARCHAR(32)," +
                    "first_join BIGINT NOT NULL," +
                    "last_join BIGINT NOT NULL," +
                    "autosell BOOLEAN NOT NULL," +
                    "autopickup BOOLEAN NOT NULL," +
                    "autosmelt BOOLEAN NOT NULL," +
                    "autoblock BOOLEAN NOT NULL," +
                    "lifetime_blocks BIGINT NOT NULL," +
                    "daily_blocks BIGINT NOT NULL," +
                    "weekly_blocks BIGINT NOT NULL," +
                    "monthly_blocks BIGINT NOT NULL," +
                    "daily_period VARCHAR(16) NOT NULL," +
                    "weekly_period VARCHAR(16) NOT NULL," +
                    "monthly_period VARCHAR(16) NOT NULL," +
                    "money_earned VARCHAR(80) NOT NULL," +
                    "data_version INTEGER NOT NULL," +
                    "updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_personal_boosters (" +
                    "id VARCHAR(64) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, multiplier VARCHAR(40) NOT NULL," +
                    "expires_at BIGINT NOT NULL, created_at BIGINT NOT NULL, metadata TEXT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_server_boosters (" +
                    "id VARCHAR(64) PRIMARY KEY, multiplier VARCHAR(40) NOT NULL, expires_at BIGINT NOT NULL," +
                    "created_at BIGINT NOT NULL, activated_by VARCHAR(36), metadata TEXT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_mine_runtime (" +
                    "mine_id VARCHAR(32) PRIMARY KEY, remaining_blocks BIGINT NOT NULL DEFAULT 0," +
                    "reset_count BIGINT NOT NULL DEFAULT 0, last_reset BIGINT NOT NULL DEFAULT 0," +
                    "next_reset BIGINT NOT NULL DEFAULT 0, last_reset_duration_ms BIGINT NOT NULL DEFAULT 0," +
                    "state VARCHAR(24) NOT NULL DEFAULT 'IDLE')");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_migration_history (" +
                    "migration_id VARCHAR(64) PRIMARY KEY, source_checksum VARCHAR(128) NOT NULL," +
                    "started_at BIGINT NOT NULL, completed_at BIGINT, result TEXT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_backup_history (" +
                    "backup_id VARCHAR(64) PRIMARY KEY, backup_type VARCHAR(24) NOT NULL, created_at BIGINT NOT NULL," +
                    "size_bytes BIGINT NOT NULL, checksum VARCHAR(128) NOT NULL, result TEXT)");
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 1);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }


    private static void applyMigrationTwo(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_personal_multipliers (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY,multiplier VARCHAR(40) NOT NULL,updated_at BIGINT NOT NULL)");
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 2);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationThree(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_booster_pauses (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY,paused_at BIGINT NOT NULL)");
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 3);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }


    private static void applyMigrationFour(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_player_statistics (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY,items_sold BIGINT NOT NULL DEFAULT 0," +
                    "rankups BIGINT NOT NULL DEFAULT 0,prestiges BIGINT NOT NULL DEFAULT 0," +
                    "boosters_used BIGINT NOT NULL DEFAULT 0,playtime_seconds BIGINT NOT NULL DEFAULT 0," +
                    "updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_mine_statistics (" +
                    "mine_id VARCHAR(32) PRIMARY KEY,lifetime_blocks BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_player_mine_statistics (" +
                    "player_uuid VARCHAR(36) NOT NULL,mine_id VARCHAR(32) NOT NULL,lifetime_blocks BIGINT NOT NULL DEFAULT 0," +
                    "updated_at BIGINT NOT NULL,PRIMARY KEY(player_uuid,mine_id))");
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 4);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }


    private static void applyMigrationFive(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            addColumnIfMissing(connection, "rp_mine_runtime", "last_recount_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_mine_runtime", "last_recount_reason", "VARCHAR(160) NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "rp_mine_runtime", "last_recount_actor", "VARCHAR(64) NOT NULL DEFAULT ''");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_reset_failures (" +
                    "reset_id VARCHAR(64) PRIMARY KEY,mine_id VARCHAR(32) NOT NULL,reason VARCHAR(64) NOT NULL," +
                    "stage VARCHAR(32) NOT NULL,started_at BIGINT NOT NULL,failed_at BIGINT NOT NULL," +
                    "duration_ms BIGINT NOT NULL,total_blocks BIGINT NOT NULL,processed_blocks BIGINT NOT NULL," +
                    "vanilla_blocks BIGINT NOT NULL,itemsadder_blocks BIGINT NOT NULL,air_blocks BIGINT NOT NULL," +
                    "reset_count_at_failure BIGINT NOT NULL,error_summary VARCHAR(512) NOT NULL," +
                    "retry_eligible BOOLEAN NOT NULL)");
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 5);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationSix(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_progression_transactions (" +
                    "transaction_id VARCHAR(64) PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "operation_type VARCHAR(24) NOT NULL," +
                    "previous_rank VARCHAR(32)," +
                    "previous_prestige VARCHAR(32)," +
                    "target_rank VARCHAR(32)," +
                    "target_prestige VARCHAR(32)," +
                    "expected_cost VARCHAR(80) NOT NULL," +
                    "actual_withdrawn VARCHAR(80) NOT NULL DEFAULT '0'," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL," +
                    "state VARCHAR(32) NOT NULL," +
                    "recovery_attempts INTEGER NOT NULL DEFAULT 0," +
                    "failure_summary VARCHAR(512) NOT NULL DEFAULT ''," +
                    "reward_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'," +
                    "luckperms_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'," +
                    "idempotency_key VARCHAR(160) NOT NULL," +
                    "metadata TEXT)");
            createIndexIfMissing(connection, "rp_progression_transactions", "rp_progression_transactions_idem",
                    "idempotency_key", true);
            createIndexIfMissing(connection, "rp_progression_transactions", "rp_progression_transactions_player_state",
                    "player_uuid,state", false);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_reward_delivery_log (" +
                    "operation_id VARCHAR(96) NOT NULL," +
                    "reward_key VARCHAR(160) NOT NULL," +
                    "reward_type VARCHAR(32) NOT NULL," +
                    "state VARCHAR(24) NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL," +
                    "failure_summary VARCHAR(512) NOT NULL DEFAULT ''," +
                    "PRIMARY KEY(operation_id,reward_key))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_mining_statistics (" +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "period_type VARCHAR(16) NOT NULL," +
                    "period_key VARCHAR(24) NOT NULL," +
                    "dimension_type VARCHAR(32) NOT NULL," +
                    "dimension_key VARCHAR(96) NOT NULL," +
                    "blocks BIGINT NOT NULL DEFAULT 0," +
                    "updated_at BIGINT NOT NULL," +
                    "PRIMARY KEY(player_uuid,period_type,period_key,dimension_type,dimension_key))");
            createIndexIfMissing(connection, "rp_mining_statistics", "rp_mining_statistics_dimension",
                    "dimension_type,dimension_key,period_type,period_key", false);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_block_event_state (" +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "event_id VARCHAR(64) NOT NULL," +
                    "state_key VARCHAR(96) NOT NULL," +
                    "last_triggered_at BIGINT NOT NULL DEFAULT 0," +
                    "completed_at BIGINT NOT NULL DEFAULT 0," +
                    "counter BIGINT NOT NULL DEFAULT 0," +
                    "updated_at BIGINT NOT NULL," +
                    "PRIMARY KEY(player_uuid,event_id,state_key))");
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 6);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationSeven(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_leaderboard_reward_periods (" +
                    "board_id VARCHAR(64) NOT NULL," +
                    "period_id VARCHAR(64) NOT NULL," +
                    "metric VARCHAR(32) NOT NULL," +
                    "period_type VARCHAR(16) NOT NULL," +
                    "state VARCHAR(24) NOT NULL," +
                    "winners_snapshot TEXT NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL," +
                    "finalized_at BIGINT NOT NULL DEFAULT 0," +
                    "announced_at BIGINT NOT NULL DEFAULT 0," +
                    "failure_summary VARCHAR(512) NOT NULL DEFAULT ''," +
                    "PRIMARY KEY(board_id,period_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_leaderboard_reward_ledger (" +
                    "board_id VARCHAR(64) NOT NULL," +
                    "period_id VARCHAR(64) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "final_position INTEGER NOT NULL," +
                    "reward_definition_id VARCHAR(64) NOT NULL," +
                    "delivery_state VARCHAR(24) NOT NULL," +
                    "attempt_count INTEGER NOT NULL DEFAULT 0," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL," +
                    "failure_summary VARCHAR(512) NOT NULL DEFAULT ''," +
                    "PRIMARY KEY(board_id,period_id,player_uuid,reward_definition_id))");
            createIndexIfMissing(connection, "rp_leaderboard_reward_ledger", "rp_leaderboard_reward_player_state",
                    "player_uuid,delivery_state", false);
            createIndexIfMissing(connection, "rp_leaderboard_reward_ledger", "rp_leaderboard_reward_period",
                    "board_id,period_id,delivery_state", false);
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 7);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationEight(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_staff_audit (" +
                    "audit_id VARCHAR(64) PRIMARY KEY," +
                    "timestamp BIGINT NOT NULL," +
                    "staff_uuid VARCHAR(36)," +
                    "staff_name VARCHAR(64) NOT NULL," +
                    "action_type VARCHAR(64) NOT NULL," +
                    "target_type VARCHAR(64) NOT NULL," +
                    "target_id VARCHAR(128) NOT NULL," +
                    "before_summary TEXT NOT NULL," +
                    "after_summary TEXT NOT NULL," +
                    "success BOOLEAN NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "related_id VARCHAR(128) NOT NULL," +
                    "metadata TEXT NOT NULL)");
            createIndexIfMissing(connection, "rp_staff_audit", "rp_staff_audit_timestamp",
                    "timestamp", false);
            createIndexIfMissing(connection, "rp_staff_audit", "rp_staff_audit_staff",
                    "staff_uuid,timestamp", false);
            createIndexIfMissing(connection, "rp_staff_audit", "rp_staff_audit_action",
                    "action_type,timestamp", false);
            createIndexIfMissing(connection, "rp_staff_audit", "rp_staff_audit_target",
                    "target_type,target_id,timestamp", false);
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 8);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationNine(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_reward_packages (" +
                    "package_id VARCHAR(96) PRIMARY KEY," +
                    "source_system VARCHAR(48) NOT NULL," +
                    "source_operation_id VARCHAR(128) NOT NULL," +
                    "player_uuid VARCHAR(36)," +
                    "state VARCHAR(24) NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL," +
                    "frozen_payload TEXT NOT NULL," +
                    "failure_summary VARCHAR(512) NOT NULL DEFAULT '')");
            createIndexIfMissing(connection, "rp_reward_packages", "rp_reward_packages_source",
                    "source_system,source_operation_id", false);
            createIndexIfMissing(connection, "rp_reward_packages", "rp_reward_packages_state",
                    "state,updated_at", false);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_reward_components (" +
                    "package_id VARCHAR(96) NOT NULL," +
                    "component_id VARCHAR(96) NOT NULL," +
                    "source_system VARCHAR(48) NOT NULL," +
                    "source_operation_id VARCHAR(128) NOT NULL," +
                    "player_uuid VARCHAR(36)," +
                    "payload TEXT NOT NULL," +
                    "component_type VARCHAR(32) NOT NULL," +
                    "amount VARCHAR(80) NOT NULL DEFAULT '0'," +
                    "due_at BIGINT NOT NULL," +
                    "state VARCHAR(24) NOT NULL," +
                    "attempt_count INTEGER NOT NULL DEFAULT 0," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL," +
                    "last_attempt_at BIGINT NOT NULL DEFAULT 0," +
                    "completed_at BIGINT NOT NULL DEFAULT 0," +
                    "failure_summary VARCHAR(512) NOT NULL DEFAULT ''," +
                    "idempotency_key VARCHAR(160) NOT NULL," +
                    "PRIMARY KEY(package_id,component_id))");
            createIndexIfMissing(connection, "rp_reward_components", "rp_reward_components_state_due",
                    "state,due_at", false);
            createIndexIfMissing(connection, "rp_reward_components", "rp_reward_components_player_state",
                    "player_uuid,state,due_at", false);
            createIndexIfMissing(connection, "rp_reward_components", "rp_reward_components_idempotency",
                    "idempotency_key", true);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_block_event_triggers (" +
                    "trigger_id VARCHAR(160) PRIMARY KEY," +
                    "event_id VARCHAR(64) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "operation_id VARCHAR(64) NOT NULL," +
                    "block_identity VARCHAR(96) NOT NULL," +
                    "trigger_type VARCHAR(32) NOT NULL," +
                    "period_id VARCHAR(64) NOT NULL," +
                    "milestone BIGINT NOT NULL DEFAULT 0," +
                    "state VARCHAR(24) NOT NULL," +
                    "package_id VARCHAR(96) NOT NULL DEFAULT ''," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL," +
                    "failure_summary VARCHAR(512) NOT NULL DEFAULT '')");
            createIndexIfMissing(connection, "rp_block_event_triggers", "rp_block_event_trigger_unique",
                    "event_id,player_uuid,operation_id,block_identity,trigger_type,period_id,milestone", true);
            createIndexIfMissing(connection, "rp_block_event_triggers", "rp_block_event_trigger_state",
                    "state,updated_at", false);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_leaderboard_winner_snapshots (" +
                    "board_id VARCHAR(64) NOT NULL," +
                    "period_id VARCHAR(64) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "last_known_name VARCHAR(64) NOT NULL," +
                    "final_position INTEGER NOT NULL," +
                    "final_value VARCHAR(80) NOT NULL," +
                    "tie_breaker VARCHAR(160) NOT NULL," +
                    "frozen_rewards TEXT NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "PRIMARY KEY(board_id,period_id,player_uuid))");
            createIndexIfMissing(connection, "rp_leaderboard_winner_snapshots", "rp_leaderboard_winners_period",
                    "board_id,period_id,final_position", false);
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 9);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationTen(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            addColumnIfMissing(connection, "rp_reward_components", "claim_token",
                    "VARCHAR(96) NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "rp_reward_components", "claimed_by",
                    "VARCHAR(96) NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "rp_reward_components", "claimed_at",
                    "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_reward_components", "claim_expires_at",
                    "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_reward_components", "next_attempt_at",
                    "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_reward_components", "delivered_at",
                    "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_progression_transactions", "refund_state",
                    "VARCHAR(32) NOT NULL DEFAULT 'REFUND_NOT_REQUIRED'");
            addColumnIfMissing(connection, "rp_progression_transactions", "refund_amount",
                    "VARCHAR(80) NOT NULL DEFAULT '0'");
            addColumnIfMissing(connection, "rp_progression_transactions", "refund_reason",
                    "VARCHAR(256) NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "rp_progression_transactions", "refund_attempts",
                    "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_progression_transactions", "refund_last_attempt_at",
                    "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_progression_transactions", "refund_failure_summary",
                    "VARCHAR(512) NOT NULL DEFAULT ''");
            createIndexIfMissing(connection, "rp_reward_components", "rp_reward_components_claimable",
                    "state,next_attempt_at,due_at", false);
            createIndexIfMissing(connection, "rp_reward_components", "rp_reward_components_claim_expiry",
                    "state,claim_expires_at", false);
            createIndexIfMissing(connection, "rp_reward_components", "rp_reward_components_package_state",
                    "package_id,state", false);
            createIndexIfMissing(connection, "rp_progression_transactions", "rp_progression_refund_state",
                    "refund_state,updated_at", false);
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 10);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationEleven(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            addColumnIfMissing(connection, "rp_block_event_triggers", "frozen_payload",
                    "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "rp_leaderboard_reward_periods", "reward_package_count",
                    "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_leaderboard_reward_periods", "verified_at",
                    "BIGINT NOT NULL DEFAULT 0");
            createIndexIfMissing(connection, "rp_block_event_triggers", "rp_block_event_trigger_package",
                    "package_id", false);
            createIndexIfMissing(connection, "rp_leaderboard_reward_periods", "rp_leaderboard_period_state",
                    "state,updated_at", false);
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 11);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationTwelve(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_bulk_mining_transactions (" +
                    "transaction_id VARCHAR(64) PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "source VARCHAR(64) NOT NULL," +
                    "mine_id VARCHAR(64) NOT NULL," +
                    "state VARCHAR(32) NOT NULL," +
                    "block_count INTEGER NOT NULL," +
                    "consumed_count INTEGER NOT NULL DEFAULT 0," +
                    "reward_package_id VARCHAR(96) NOT NULL DEFAULT ''," +
                    "block_snapshots TEXT NOT NULL," +
                    "reward_payload TEXT NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL," +
                    "recovery_attempts INTEGER NOT NULL DEFAULT 0," +
                    "failure_summary VARCHAR(512) NOT NULL DEFAULT '')");
            createIndexIfMissing(connection, "rp_bulk_mining_transactions", "rp_bulk_mining_state",
                    "state,updated_at", false);
            createIndexIfMissing(connection, "rp_bulk_mining_transactions", "rp_bulk_mining_player_state",
                    "player_uuid,state,updated_at", false);
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 12);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationThirteen(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            addColumnIfMissing(connection, "rp_block_event_triggers", "logical_claim_key",
                    "VARCHAR(192) NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "rp_block_event_triggers", "duplicate_count",
                    "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_block_event_triggers", "recovery_attempts",
                    "INTEGER NOT NULL DEFAULT 0");
            backfillBlockEventLogicalClaims(connection);
            createIndexIfMissing(connection, "rp_block_event_triggers", "rp_block_event_logical_claim",
                    "logical_claim_key", true);
            createIndexIfMissing(connection, "rp_block_event_triggers", "rp_block_event_recovery",
                    "state,recovery_attempts,updated_at", false);

            addColumnIfMissing(connection, "rp_leaderboard_reward_ledger", "reward_package_id",
                    "VARCHAR(128) NOT NULL DEFAULT ''");
            backfillLeaderboardRewardPackageIds(connection);
            createIndexIfMissing(connection, "rp_leaderboard_reward_ledger", "rp_leaderboard_reward_package",
                    "reward_package_id", false);
            addColumnIfMissing(connection, "rp_leaderboard_reward_periods", "finalization_owner",
                    "VARCHAR(96) NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "rp_leaderboard_reward_periods", "finalization_started_at",
                    "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_leaderboard_reward_periods", "recovery_attempts",
                    "INTEGER NOT NULL DEFAULT 0");
            createIndexIfMissing(connection, "rp_leaderboard_reward_periods", "rp_leaderboard_period_recovery",
                    "state,recovery_attempts,updated_at", false);

            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 13);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationFourteen(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            addColumnIfMissing(connection, "rp_bulk_mining_transactions", "spawned_entity_ids",
                    "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "rp_leaderboard_reward_periods", "reward_plan",
                    "TEXT NOT NULL DEFAULT ''");
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 14);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationFifteen(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            addColumnIfMissing(connection, "rp_bulk_mining_transactions", "commit_payload",
                    "TEXT NOT NULL DEFAULT ''");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_bulk_mining_commits ("
                    + "transaction_id VARCHAR(64) PRIMARY KEY,"
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "committed_payload TEXT NOT NULL,"
                    + "applied_at BIGINT NOT NULL)");
            createIndexIfMissing(connection, "rp_bulk_mining_commits", "rp_bulk_mining_commits_player",
                    "player_uuid,applied_at", false);
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 15);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationSixteen(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            addColumnIfMissing(connection, "rp_personal_boosters", "starts_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_personal_boosters", "enabled", "BOOLEAN NOT NULL DEFAULT TRUE");
            addColumnIfMissing(connection, "rp_server_boosters", "starts_at", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "rp_server_boosters", "enabled", "BOOLEAN NOT NULL DEFAULT TRUE");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE rp_personal_boosters SET starts_at=created_at WHERE starts_at=0");
                statement.executeUpdate("UPDATE rp_server_boosters SET starts_at=created_at WHERE starts_at=0");
            }
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 16);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void applyMigrationSeventeen(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gangs ("
                    + "gang_id VARCHAR(36) PRIMARY KEY,name VARCHAR(32) NOT NULL,normalized_name VARCHAR(32) NOT NULL,"
                    + "tag VARCHAR(12) NOT NULL,normalized_tag VARCHAR(12) NOT NULL,description VARCHAR(256) NOT NULL,"
                    + "owner_uuid VARCHAR(36) NOT NULL,created_at BIGINT NOT NULL,level INTEGER NOT NULL,"
                    + "xp DECIMAL(38,4) NOT NULL,points BIGINT NOT NULL,bank_balance DECIMAL(38,2) NOT NULL,"
                    + "member_limit INTEGER NOT NULL,member_count INTEGER NOT NULL,join_mode VARCHAR(24) NOT NULL,"
                    + "color VARCHAR(32) NOT NULL,motd VARCHAR(256) NOT NULL,home_world VARCHAR(64),"
                    + "home_x DOUBLE,home_y DOUBLE,home_z DOUBLE,home_yaw FLOAT,home_pitch FLOAT,"
                    + "enabled BOOLEAN NOT NULL,version BIGINT NOT NULL,updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_ranks ("
                    + "rank_id VARCHAR(36) PRIMARY KEY,gang_id VARCHAR(36) NOT NULL,system_key VARCHAR(24),"
                    + "display_name VARCHAR(32) NOT NULL,priority INTEGER NOT NULL,color VARCHAR(32) NOT NULL,"
                    + "is_default BOOLEAN NOT NULL,created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_rank_permissions ("
                    + "rank_id VARCHAR(36) NOT NULL,permission VARCHAR(48) NOT NULL,enabled BOOLEAN NOT NULL,"
                    + "PRIMARY KEY(rank_id,permission))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_members ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY,gang_id VARCHAR(36) NOT NULL,rank_id VARCHAR(36) NOT NULL,"
                    + "joined_at BIGINT NOT NULL,last_seen_at BIGINT NOT NULL,chat_enabled BOOLEAN NOT NULL,"
                    + "version BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_invites ("
                    + "invite_id VARCHAR(36) PRIMARY KEY,gang_id VARCHAR(36) NOT NULL,player_uuid VARCHAR(36) NOT NULL,"
                    + "invited_by VARCHAR(36) NOT NULL,created_at BIGINT NOT NULL,expires_at BIGINT NOT NULL,status VARCHAR(16) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_bank_transactions ("
                    + "transaction_id VARCHAR(36) PRIMARY KEY,gang_id VARCHAR(36) NOT NULL,player_uuid VARCHAR(36),"
                    + "amount DECIMAL(38,2) NOT NULL,previous_balance DECIMAL(38,2) NOT NULL,"
                    + "new_balance DECIMAL(38,2) NOT NULL,transaction_type VARCHAR(32) NOT NULL,"
                    + "reason VARCHAR(256) NOT NULL,created_at BIGINT NOT NULL,operation_key VARCHAR(128) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_withdrawal_usage ("
                    + "gang_id VARCHAR(36) NOT NULL,player_uuid VARCHAR(36) NOT NULL,period_key VARCHAR(16) NOT NULL,"
                    + "amount DECIMAL(38,2) NOT NULL,PRIMARY KEY(gang_id,player_uuid,period_key))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_upgrades ("
                    + "gang_id VARCHAR(36) NOT NULL,upgrade_id VARCHAR(48) NOT NULL,tier INTEGER NOT NULL,"
                    + "updated_at BIGINT NOT NULL,updated_by VARCHAR(36),PRIMARY KEY(gang_id,upgrade_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_statistics ("
                    + "gang_id VARCHAR(36) PRIMARY KEY,blocks_mined BIGINT NOT NULL,money_earned DECIMAL(38,2) NOT NULL,"
                    + "gang_xp_earned DECIMAL(38,4) NOT NULL,rankups BIGINT NOT NULL,prestiges BIGINT NOT NULL,"
                    + "block_events BIGINT NOT NULL,updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_member_statistics ("
                    + "gang_id VARCHAR(36) NOT NULL,player_uuid VARCHAR(36) NOT NULL,blocks_mined BIGINT NOT NULL,"
                    + "money_earned DECIMAL(38,2) NOT NULL,gang_xp_earned DECIMAL(38,4) NOT NULL,rankups BIGINT NOT NULL,"
                    + "prestiges BIGINT NOT NULL,block_events BIGINT NOT NULL,updated_at BIGINT NOT NULL,"
                    + "PRIMARY KEY(gang_id,player_uuid))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_period_statistics ("
                    + "gang_id VARCHAR(36) NOT NULL,period_type VARCHAR(16) NOT NULL,period_key VARCHAR(48) NOT NULL,"
                    + "blocks_mined BIGINT NOT NULL,money_earned DECIMAL(38,2) NOT NULL,"
                    + "gang_xp_earned DECIMAL(38,4) NOT NULL,rankups BIGINT NOT NULL,prestiges BIGINT NOT NULL,"
                    + "block_events BIGINT NOT NULL,updated_at BIGINT NOT NULL,"
                    + "PRIMARY KEY(gang_id,period_type,period_key))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_boosters ("
                    + "booster_id VARCHAR(36) PRIMARY KEY,gang_id VARCHAR(36) NOT NULL,booster_type VARCHAR(32) NOT NULL,"
                    + "multiplier DECIMAL(18,6) NOT NULL,starts_at BIGINT NOT NULL,expires_at BIGINT NOT NULL,"
                    + "activated_by VARCHAR(36),enabled BOOLEAN NOT NULL,source VARCHAR(64) NOT NULL,created_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_missions ("
                    + "mission_instance_id VARCHAR(96) PRIMARY KEY,gang_id VARCHAR(36) NOT NULL,mission_id VARCHAR(48) NOT NULL,"
                    + "period_key VARCHAR(32) NOT NULL,progress DECIMAL(38,4) NOT NULL,target DECIMAL(38,4) NOT NULL,"
                    + "state VARCHAR(16) NOT NULL,completed_at BIGINT,claimed_at BIGINT,updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_mission_progress ("
                    + "mission_instance_id VARCHAR(96) NOT NULL,player_uuid VARCHAR(36) NOT NULL,progress DECIMAL(38,4) NOT NULL,"
                    + "updated_at BIGINT NOT NULL,PRIMARY KEY(mission_instance_id,player_uuid))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_audit ("
                    + "audit_id VARCHAR(36) PRIMARY KEY,gang_id VARCHAR(36) NOT NULL,actor_uuid VARCHAR(36),"
                    + "action_type VARCHAR(64) NOT NULL,target_type VARCHAR(32) NOT NULL,target_id VARCHAR(128) NOT NULL,"
                    + "before_summary VARCHAR(2048) NOT NULL,after_summary VARCHAR(2048) NOT NULL,success BOOLEAN NOT NULL,"
                    + "reason VARCHAR(512) NOT NULL,metadata VARCHAR(2048) NOT NULL,created_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_seasons ("
                    + "season_id VARCHAR(48) PRIMARY KEY,starts_at BIGINT NOT NULL,ends_at BIGINT NOT NULL,"
                    + "tracked_categories VARCHAR(512) NOT NULL,reward_plan TEXT NOT NULL,state VARCHAR(16) NOT NULL,"
                    + "created_at BIGINT NOT NULL,finalized_at BIGINT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_season_results ("
                    + "season_id VARCHAR(48) NOT NULL,category VARCHAR(48) NOT NULL,position INTEGER NOT NULL,"
                    + "gang_id VARCHAR(36) NOT NULL,gang_name VARCHAR(32) NOT NULL,value DECIMAL(38,4) NOT NULL,"
                    + "frozen_reward TEXT NOT NULL,reward_state VARCHAR(24) NOT NULL,recorded_at BIGINT NOT NULL,"
                    + "PRIMARY KEY(season_id,category,position))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS rp_gang_operation_claims ("
                    + "operation_key VARCHAR(128) PRIMARY KEY,gang_id VARCHAR(36),operation_type VARCHAR(48) NOT NULL,"
                    + "result_payload TEXT NOT NULL,created_at BIGINT NOT NULL)");
            createIndexIfMissing(connection, "rp_gangs", "rp_gangs_name_unique", "normalized_name", true);
            createIndexIfMissing(connection, "rp_gangs", "rp_gangs_tag_unique", "normalized_tag", true);
            createIndexIfMissing(connection, "rp_gang_ranks", "rp_gang_ranks_system_unique", "gang_id,system_key", true);
            createIndexIfMissing(connection, "rp_gang_ranks", "rp_gang_ranks_priority_unique", "gang_id,priority", true);
            createIndexIfMissing(connection, "rp_gang_invites", "rp_gang_invites_active_lookup", "player_uuid,status,expires_at", false);
            createIndexIfMissing(connection, "rp_gang_invites", "rp_gang_invites_gang_player", "gang_id,player_uuid,status", false);
            createIndexIfMissing(connection, "rp_gang_bank_transactions", "rp_gang_bank_operation_unique", "operation_key", true);
            createIndexIfMissing(connection, "rp_gang_bank_transactions", "rp_gang_bank_history", "gang_id,created_at", false);
            createIndexIfMissing(connection, "rp_gang_audit", "rp_gang_audit_history", "gang_id,created_at", false);
            createIndexIfMissing(connection, "rp_gang_period_statistics", "rp_gang_period_board",
                    "period_type,period_key", false);
            createIndexIfMissing(connection, "rp_gang_season_results", "rp_gang_season_gang", "gang_id,season_id", false);
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rp_schema_version(version, applied_at) VALUES (?,?)")) {
                insert.setInt(1, 17);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void backfillBlockEventLogicalClaims(Connection connection) throws SQLException {
        try (java.sql.PreparedStatement select = connection.prepareStatement(
                "SELECT trigger_id FROM rp_block_event_triggers "
                        + "WHERE logical_claim_key='' OR logical_claim_key IS NULL");
             ResultSet rows = select.executeQuery();
             java.sql.PreparedStatement update = connection.prepareStatement(
                     "UPDATE rp_block_event_triggers SET logical_claim_key=? WHERE trigger_id=?")) {
            while (rows.next()) {
                String triggerId = rows.getString("trigger_id");
                update.setString(1, triggerId);
                update.setString(2, triggerId);
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private static void backfillLeaderboardRewardPackageIds(Connection connection) throws SQLException {
        try (java.sql.PreparedStatement select = connection.prepareStatement(
                "SELECT board_id,period_id,player_uuid,reward_definition_id "
                        + "FROM rp_leaderboard_reward_ledger "
                        + "WHERE reward_package_id='' OR reward_package_id IS NULL");
             ResultSet rows = select.executeQuery();
             java.sql.PreparedStatement update = connection.prepareStatement(
                     "UPDATE rp_leaderboard_reward_ledger SET reward_package_id=? "
                             + "WHERE board_id=? AND period_id=? AND player_uuid=? AND reward_definition_id=?")) {
            while (rows.next()) {
                String boardId = rows.getString("board_id");
                String periodId = rows.getString("period_id");
                String playerId = rows.getString("player_uuid");
                String rewardId = rows.getString("reward_definition_id");
                update.setString(1, deterministicLeaderboardPackageId(boardId, periodId, playerId, rewardId));
                update.setString(2, boardId);
                update.setString(3, periodId);
                update.setString(4, playerId);
                update.setString(5, rewardId);
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private static String deterministicLeaderboardPackageId(String boardId, String periodId, String playerId,
                                                            String rewardId) {
        return ("lb-" + boardId + '-' + periodId + '-' + playerId + '-' + rewardId)
                .replaceAll("[^A-Za-z0-9_.:-]", "_").toLowerCase(java.util.Locale.ROOT);
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        try (ResultSet result = connection.getMetaData().getColumns(null, null, table, column)) {
            if (result.next()) return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static void createIndexIfMissing(Connection connection, String table, String index, String columns,
                                             boolean unique) throws SQLException {
        try (ResultSet result = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
            while (result.next()) {
                if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + index + " ON " + table
                    + "(" + columns + ")");
        }
    }

    /** Submits work once. Use for operations that must never be retried automatically. */
    public <T> CompletableFuture<T> submit(SqlFunction<T> work) {
        return enqueue(work, false);
    }

    /** Submits an idempotent operation and retries only transient database failures. */
    public <T> CompletableFuture<T> submitIdempotent(SqlFunction<T> work) {
        return enqueue(work, true);
    }

    private <T> CompletableFuture<T> enqueue(SqlFunction<T> work, boolean retryTransient) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                int attempts = retryTransient ? config.retryCount() + 1 : 1;
                for (int attempt = 1; attempt <= attempts; attempt++) {
                    try {
                        T result = withConnectionSync(work);
                        future.complete(result);
                        if (transientFailureObserved.compareAndSet(true, false)) notifyReconnectListeners();
                        return;
                    } catch (Throwable error) {
                        if (isTransient(error)) transientFailureObserved.set(true);
                        if (attempt >= attempts || !isTransient(error)) {
                            future.completeExceptionally(error instanceof RuntimeException ? error : new DatabaseException(error));
                            return;
                        }
                        try {
                            Thread.sleep((long) config.retryDelayMillis() * attempt);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            future.completeExceptionally(interrupted);
                            return;
                        }
                    }
                }
            });
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(new DatabaseQueueFullException(config.queueCapacity(), ex));
        }
        return future;
    }

    private static boolean isTransient(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLTransientException) return true;
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && state.startsWith("08")) return true;
                if (sql.getErrorCode() == 5 || sql.getErrorCode() == 6) return true;
                String message = sql.getMessage();
                if (message != null) {
                    String normalized = message.toLowerCase(java.util.Locale.ROOT);
                    if (normalized.contains("sqlite_busy")
                            || normalized.contains("sqlite_locked")
                            || normalized.contains("database is locked")) return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private <T> T withConnectionSync(SqlFunction<T> work) throws Exception {
        SimpleConnectionPool activePool = pool;
        if (activePool == null) throw new IllegalStateException("Database has not been initialized");
        Connection connection = activePool.borrow();
        try { return work.apply(connection); }
        finally { activePool.release(connection); }
    }

    public int queueSize() { return executor.getQueue().size(); }
    public int queueCapacity() { return config.queueCapacity(); }
    public StorageConfig.Type storageType() { return config.type(); }

    public void addReconnectListener(Runnable listener) {
        reconnectListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void notifyReconnectListeners() {
        for (Runnable listener : reconnectListeners) {
            try {
                listener.run();
            } catch (RuntimeException error) {
                logger.severe("Database reconnect listener failed: " + rootMessage(error));
            }
        }
    }

    public PoolSnapshot poolSnapshot() {
        SimpleConnectionPool activePool = pool;
        if (activePool == null) return new PoolSnapshot(false, 0, 0, 0, true);
        return activePool.snapshot();
    }

    public CompletableFuture<DatabaseHealth> health() {
        long started = System.nanoTime();
        return submitIdempotent(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }
            long elapsedMillis = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            return new DatabaseHealth(true, "ok", elapsedMillis, queueSize(), queueCapacity(), poolSnapshot());
        }).exceptionally(error -> new DatabaseHealth(false, rootMessage(error),
                Math.max(0L, (System.nanoTime() - started) / 1_000_000L), queueSize(), queueCapacity(),
                poolSnapshot()));
    }

    @Override public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(config.shutdownFlushTimeoutSeconds(), TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        SimpleConnectionPool activePool = pool;
        if (activePool != null) activePool.close();
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws Exception;
    }

    public static final class DatabaseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public DatabaseException(Throwable cause) { super(cause); }
    }

    public static final class DatabaseQueueFullException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public DatabaseQueueFullException(int capacity, Throwable cause) {
            super("Database queue reached its capacity of " + capacity, cause);
        }
    }

    public record PoolSnapshot(boolean initialized, int created, int idle, int maxSize, boolean closed) { }

    public record DatabaseHealth(boolean healthy, String summary, long latencyMillis, int queueSize,
                                 int queueCapacity, PoolSnapshot pool) { }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
