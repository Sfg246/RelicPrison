package site.mcrelicworld.relicprison.mining;

import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BulkMiningCommitRepository {
    private final DatabaseManager database;
    private final CommitObserver observer;

    public BulkMiningCommitRepository(DatabaseManager database) {
        this(database, CommitObserver.NONE);
    }

    public BulkMiningCommitRepository(DatabaseManager database, CommitObserver observer) {
        this.database = database;
        this.observer = observer == null ? CommitObserver.NONE : observer;
    }

    public CompletableFuture<Boolean> apply(String transactionId, UUID playerId,
                                            BulkMiningCommittedResult result) {
        return database.submitIdempotent(connection -> apply(connection, transactionId, playerId, result));
    }

    private boolean apply(Connection connection, String transactionId, UUID playerId,
                          BulkMiningCommittedResult result) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            boolean inserted = insertCommit(connection, transactionId, playerId, result);
            if (inserted) {
                updateProfile(connection, playerId, result);
                observer.afterProgression();
                updatePlayerStatistics(connection, playerId, result);
                updateMiningStatistics(connection, playerId, result);
                observer.afterStatistics();
                if (result.mineAnalytics()) updateMineAnalytics(connection, playerId, result);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_bulk_mining_transactions SET state='FINALIZING',updated_at=?,failure_summary='' "
                            + "WHERE transaction_id=? AND state NOT IN ('COMMITTED','ROLLED_BACK')")) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, transactionId);
                statement.executeUpdate();
            }
            connection.commit();
            return inserted;
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private boolean insertCommit(Connection connection, String transactionId, UUID playerId,
                                 BulkMiningCommittedResult result) throws SQLException {
        String sql = database.storageType() == StorageConfig.Type.MYSQL
                ? "INSERT IGNORE INTO rp_bulk_mining_commits(transaction_id,player_uuid,committed_payload,applied_at) VALUES(?,?,?,?)"
                : "INSERT OR IGNORE INTO rp_bulk_mining_commits(transaction_id,player_uuid,committed_payload,applied_at) VALUES(?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, transactionId);
            statement.setString(2, playerId.toString());
            statement.setString(3, result.encode());
            statement.setLong(4, System.currentTimeMillis());
            return statement.executeUpdate() == 1;
        }
    }

    private static void updateProfile(Connection connection, UUID playerId,
                                      BulkMiningCommittedResult result) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT lifetime_blocks,daily_blocks,weekly_blocks,monthly_blocks,daily_period,weekly_period,"
                        + "monthly_period,money_earned FROM rp_player_profiles WHERE uuid=?")) {
            select.setString(1, playerId.toString());
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) throw new SQLException("Player profile missing for bulk commit " + playerId);
                Map<String, String> periods = new HashMap<>();
                for (BulkMiningCommittedResult.Period period : result.periods()) {
                    periods.put(period.type(), period.key());
                }
                String daily = periods.getOrDefault("daily", row.getString("daily_period"));
                String weekly = periods.getOrDefault("weekly", row.getString("weekly_period"));
                String monthly = periods.getOrDefault("monthly", row.getString("monthly_period"));
                long dailyBlocks = daily.equals(row.getString("daily_period")) ? row.getLong("daily_blocks") : 0L;
                long weeklyBlocks = weekly.equals(row.getString("weekly_period")) ? row.getLong("weekly_blocks") : 0L;
                long monthlyBlocks = monthly.equals(row.getString("monthly_period")) ? row.getLong("monthly_blocks") : 0L;
                BigDecimal money = new BigDecimal(row.getString("money_earned")).add(result.moneyEarned());
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE rp_player_profiles SET lifetime_blocks=?,daily_blocks=?,weekly_blocks=?,"
                                + "monthly_blocks=?,daily_period=?,weekly_period=?,monthly_period=?,money_earned=?,"
                                + "updated_at=? WHERE uuid=?")) {
                    update.setLong(1, row.getLong("lifetime_blocks") + result.totalBlocks());
                    update.setLong(2, dailyBlocks + result.totalBlocks());
                    update.setLong(3, weeklyBlocks + result.totalBlocks());
                    update.setLong(4, monthlyBlocks + result.totalBlocks());
                    update.setString(5, daily);
                    update.setString(6, weekly);
                    update.setString(7, monthly);
                    update.setString(8, money.toPlainString());
                    update.setLong(9, System.currentTimeMillis());
                    update.setString(10, playerId.toString());
                    if (update.executeUpdate() != 1) throw new SQLException("Player profile disappeared during bulk commit");
                }
            }
        }
    }

    private void updatePlayerStatistics(Connection connection, UUID playerId,
                                        BulkMiningCommittedResult result) throws SQLException {
        if (result.itemsSold() <= 0) return;
        String sql = database.storageType() == StorageConfig.Type.MYSQL
                ? "INSERT INTO rp_player_statistics(player_uuid,items_sold,rankups,prestiges,boosters_used,playtime_seconds,updated_at) VALUES(?,?,0,0,0,0,?) ON DUPLICATE KEY UPDATE items_sold=items_sold+VALUES(items_sold),updated_at=VALUES(updated_at)"
                : "INSERT INTO rp_player_statistics(player_uuid,items_sold,rankups,prestiges,boosters_used,playtime_seconds,updated_at) VALUES(?,?,0,0,0,0,?) ON CONFLICT(player_uuid) DO UPDATE SET items_sold=rp_player_statistics.items_sold+excluded.items_sold,updated_at=excluded.updated_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, result.itemsSold());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void updateMiningStatistics(Connection connection, UUID playerId,
                                        BulkMiningCommittedResult result) throws SQLException {
        String sql = database.storageType() == StorageConfig.Type.MYSQL
                ? "INSERT INTO rp_mining_statistics(player_uuid,period_type,period_key,dimension_type,dimension_key,blocks,updated_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE blocks=blocks+VALUES(blocks),updated_at=VALUES(updated_at)"
                : "INSERT INTO rp_mining_statistics(player_uuid,period_type,period_key,dimension_type,dimension_key,blocks,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(player_uuid,period_type,period_key,dimension_type,dimension_key) DO UPDATE SET blocks=rp_mining_statistics.blocks+excluded.blocks,updated_at=excluded.updated_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            for (BulkMiningCommittedResult.Period period : result.periods()) {
                for (BulkMiningCommittedResult.MineResult mine : result.mines()) {
                    Map<Dimension, Long> dimensions = dimensions(mine);
                    for (var entry : dimensions.entrySet()) {
                        statement.setString(1, playerId.toString());
                        statement.setString(2, period.type());
                        statement.setString(3, period.key());
                        statement.setString(4, entry.getKey().type());
                        statement.setString(5, entry.getKey().key());
                        statement.setLong(6, entry.getValue());
                        statement.setLong(7, now);
                        statement.addBatch();
                    }
                }
            }
            statement.executeBatch();
        }
    }

    private void updateMineAnalytics(Connection connection, UUID playerId,
                                     BulkMiningCommittedResult result) throws SQLException {
        String mineSql = database.storageType() == StorageConfig.Type.MYSQL
                ? "INSERT INTO rp_mine_statistics(mine_id,lifetime_blocks,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE lifetime_blocks=lifetime_blocks+VALUES(lifetime_blocks),updated_at=VALUES(updated_at)"
                : "INSERT INTO rp_mine_statistics(mine_id,lifetime_blocks,updated_at) VALUES(?,?,?) ON CONFLICT(mine_id) DO UPDATE SET lifetime_blocks=rp_mine_statistics.lifetime_blocks+excluded.lifetime_blocks,updated_at=excluded.updated_at";
        String playerMineSql = database.storageType() == StorageConfig.Type.MYSQL
                ? "INSERT INTO rp_player_mine_statistics(player_uuid,mine_id,lifetime_blocks,updated_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE lifetime_blocks=lifetime_blocks+VALUES(lifetime_blocks),updated_at=VALUES(updated_at)"
                : "INSERT INTO rp_player_mine_statistics(player_uuid,mine_id,lifetime_blocks,updated_at) VALUES(?,?,?,?) ON CONFLICT(player_uuid,mine_id) DO UPDATE SET lifetime_blocks=rp_player_mine_statistics.lifetime_blocks+excluded.lifetime_blocks,updated_at=excluded.updated_at";
        long now = System.currentTimeMillis();
        try (PreparedStatement mine = connection.prepareStatement(mineSql);
             PreparedStatement playerMine = connection.prepareStatement(playerMineSql)) {
            for (BulkMiningCommittedResult.MineResult resultMine : result.mines()) {
                mine.setString(1, resultMine.mineId().toLowerCase(Locale.ROOT));
                mine.setLong(2, resultMine.blocks());
                mine.setLong(3, now);
                mine.addBatch();
                playerMine.setString(1, playerId.toString());
                playerMine.setString(2, resultMine.mineId().toLowerCase(Locale.ROOT));
                playerMine.setLong(3, resultMine.blocks());
                playerMine.setLong(4, now);
                playerMine.addBatch();
            }
            mine.executeBatch();
            playerMine.executeBatch();
        }
    }

    public static Map<Dimension, Long> dimensions(BulkMiningCommittedResult.MineResult mine) {
        Map<Dimension, Long> values = new HashMap<>();
        merge(values, "mine", mine.mineId().toLowerCase(Locale.ROOT), mine.blocks());
        merge(values, "source", "bulk", mine.blocks());
        merge(values, "total", "blocks", mine.blocks());
        merge(values, "flag", "autosell", mine.autoSellBlocks());
        merge(values, "flag", "autopickup", mine.autoPickupBlocks());
        merge(values, "flag", "autoblock", mine.autoBlockBlocks());
        merge(values, "delivery", "fallback_dropped_items", mine.fallbackDroppedItems());
        mine.materials().forEach((key, amount) -> merge(values, "material", key.toLowerCase(Locale.ROOT), amount));
        mine.customBlocks().forEach((key, amount) -> merge(values, "custom_block", key.toLowerCase(Locale.ROOT), amount));
        return Map.copyOf(values);
    }

    private static void merge(Map<Dimension, Long> values, String type, String key, long amount) {
        if (amount > 0) values.merge(new Dimension(type, key), amount, Long::sum);
    }

    public record Dimension(String type, String key) { }

    public interface CommitObserver {
        CommitObserver NONE = new CommitObserver() { };

        default void afterProgression() { }

        default void afterStatistics() { }
    }
}
