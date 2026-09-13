package site.mcrelicworld.relicprison.booster;

import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BoosterRepository {
    private final DatabaseManager database;
    public BoosterRepository(DatabaseManager database) { this.database = database; }

    public CompletableFuture<List<ActiveBooster>> loadActive(long now) {
        return database.submitIdempotent(connection -> {
            List<ActiveBooster> result = new ArrayList<>();
            try (PreparedStatement personal = connection.prepareStatement(
                    "SELECT id,player_uuid,multiplier,expires_at,created_at,metadata,starts_at,enabled FROM rp_personal_boosters " +
                            "WHERE expires_at>? OR player_uuid IN (SELECT player_uuid FROM rp_booster_pauses)")) {
                personal.setLong(1, now);
                try (ResultSet rows = personal.executeQuery()) {
                    while (rows.next()) result.add(new ActiveBooster(rows.getString(1), UUID.fromString(rows.getString(2)),
                            new BigDecimal(rows.getString(3)), rows.getLong(4), rows.getLong(5), false, rows.getString(6),
                            rows.getLong(7), rows.getBoolean(8)));
                }
            }
            try (PreparedStatement server = connection.prepareStatement(
                    "SELECT id,multiplier,expires_at,created_at,activated_by,metadata,starts_at,enabled FROM rp_server_boosters WHERE expires_at>?")) {
                server.setLong(1, now);
                try (ResultSet rows = server.executeQuery()) {
                    while (rows.next()) result.add(new ActiveBooster(rows.getString(1), null,
                            new BigDecimal(rows.getString(2)), rows.getLong(3), rows.getLong(4), true, rows.getString(5),
                            rows.getLong(7), rows.getBoolean(8)));
                }
            }
            try (PreparedStatement expiredPersonal = connection.prepareStatement(
                    "DELETE FROM rp_personal_boosters WHERE expires_at<=? " +
                            "AND player_uuid NOT IN (SELECT player_uuid FROM rp_booster_pauses)");
                 PreparedStatement expiredServer = connection.prepareStatement("DELETE FROM rp_server_boosters WHERE expires_at<=?")) {
                expiredPersonal.setLong(1, now); expiredPersonal.executeUpdate();
                expiredServer.setLong(1, now); expiredServer.executeUpdate();
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<Void> save(ActiveBooster booster) {
        return database.submitIdempotent(connection -> {
            try { save(connection, booster); return null; }
            catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<Void> replace(ActiveBooster before, ActiveBooster after) {
        return database.submitIdempotent(connection -> {
            boolean autoCommit = true;
            try {
                autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                delete(connection, "rp_personal_boosters", before.id());
                delete(connection, "rp_server_boosters", before.id());
                save(connection, after);
                connection.commit();
                return null;
            } catch (SQLException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw new DatabaseManager.DatabaseException(ex);
            } finally {
                try { connection.setAutoCommit(autoCommit); } catch (SQLException ignored) { }
            }
        });
    }

    public CompletableFuture<Void> delete(ActiveBooster booster) {
        return database.submitIdempotent(connection -> {
            String table = booster.serverWide() ? "rp_server_boosters" : "rp_personal_boosters";
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
                statement.setString(1, booster.id()); statement.executeUpdate(); return null;
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<BigDecimal> loadPermanentMultiplier(UUID playerId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT multiplier FROM rp_personal_multipliers WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? new BigDecimal(rows.getString(1)) : BigDecimal.ONE;
                }
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<Void> savePermanentMultiplier(UUID playerId, BigDecimal multiplier) {
        return database.submitIdempotent(connection -> {
            String clause = database.storageType() == StorageConfig.Type.MYSQL
                    ? "ON DUPLICATE KEY UPDATE multiplier=VALUES(multiplier),updated_at=VALUES(updated_at)"
                    : "ON CONFLICT(player_uuid) DO UPDATE SET multiplier=excluded.multiplier,updated_at=excluded.updated_at";
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rp_personal_multipliers(player_uuid,multiplier,updated_at) VALUES(?,?,?) " + clause)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, multiplier.toPlainString());
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate(); return null;
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<java.util.Set<UUID>> loadPausedPlayers() {
        return database.submitIdempotent(connection -> {
            java.util.Set<UUID> result = new java.util.HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_uuid FROM rp_booster_pauses");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(UUID.fromString(rows.getString(1)));
                return java.util.Set.copyOf(result);
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<Void> pause(UUID playerId, long pausedAt) {
        return database.submitIdempotent(connection -> {
            String clause = database.storageType() == StorageConfig.Type.MYSQL
                    ? "ON DUPLICATE KEY UPDATE paused_at=VALUES(paused_at)"
                    : "ON CONFLICT(player_uuid) DO UPDATE SET paused_at=excluded.paused_at";
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rp_booster_pauses(player_uuid,paused_at) VALUES(?,?) " + clause)) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, pausedAt);
                statement.executeUpdate();
                return null;
            } catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
        });
    }

    public CompletableFuture<Long> resume(UUID playerId, long resumedAt) {
        return database.submitIdempotent(connection -> {
            long pausedAt = 0L;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT paused_at FROM rp_booster_pauses WHERE player_uuid=?")) {
                select.setString(1, playerId.toString());
                try (ResultSet rows = select.executeQuery()) { if (rows.next()) pausedAt = rows.getLong(1); }
            }
            if (pausedAt <= 0L) return 0L;
            long extension = Math.max(0L, resumedAt - pausedAt);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE rp_personal_boosters SET expires_at=expires_at+?," +
                            "starts_at=CASE WHEN starts_at>? THEN starts_at+? ELSE starts_at END WHERE player_uuid=?");
                 PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM rp_booster_pauses WHERE player_uuid=?")) {
                update.setLong(1, extension);
                update.setLong(2, resumedAt);
                update.setLong(3, extension);
                update.setString(4, playerId.toString());
                update.executeUpdate();
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
            }
            return extension;
        });
    }

    private String upsertPersonal() {
        return database.storageType() == StorageConfig.Type.MYSQL
                ? "ON DUPLICATE KEY UPDATE player_uuid=VALUES(player_uuid),multiplier=VALUES(multiplier),expires_at=VALUES(expires_at),metadata=VALUES(metadata),starts_at=VALUES(starts_at),enabled=VALUES(enabled)"
                : "ON CONFLICT(id) DO UPDATE SET player_uuid=excluded.player_uuid,multiplier=excluded.multiplier,expires_at=excluded.expires_at,metadata=excluded.metadata,starts_at=excluded.starts_at,enabled=excluded.enabled";
    }
    private String upsertServer() {
        return database.storageType() == StorageConfig.Type.MYSQL
                ? "ON DUPLICATE KEY UPDATE multiplier=VALUES(multiplier),expires_at=VALUES(expires_at),activated_by=VALUES(activated_by),metadata=VALUES(metadata),starts_at=VALUES(starts_at),enabled=VALUES(enabled)"
                : "ON CONFLICT(id) DO UPDATE SET multiplier=excluded.multiplier,expires_at=excluded.expires_at,activated_by=excluded.activated_by,metadata=excluded.metadata,starts_at=excluded.starts_at,enabled=excluded.enabled";
    }

    private void save(Connection connection, ActiveBooster booster) throws SQLException {
        String sql;
        if (booster.serverWide()) {
            sql = "INSERT INTO rp_server_boosters(id,multiplier,expires_at,created_at,activated_by,metadata,starts_at,enabled) VALUES(?,?,?,?,?,?,?,?) "
                    + upsertServer();
        } else {
            sql = "INSERT INTO rp_personal_boosters(id,player_uuid,multiplier,expires_at,created_at,metadata,starts_at,enabled) VALUES(?,?,?,?,?,?,?,?) "
                    + upsertPersonal();
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, booster.id());
            int index = 2;
            if (!booster.serverWide()) statement.setString(index++, booster.owner().toString());
            statement.setString(index++, booster.multiplier().toPlainString());
            statement.setLong(index++, booster.expiresAt());
            statement.setLong(index++, booster.createdAt());
            if (booster.serverWide()) statement.setString(index++, booster.activatedBy());
            statement.setString(index++, booster.activatedBy());
            statement.setLong(index++, booster.startsAt());
            statement.setBoolean(index, booster.enabled());
            statement.executeUpdate();
        }
    }

    private static void delete(Connection connection, String table, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }
}
