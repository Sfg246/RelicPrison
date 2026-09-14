package site.mcrelicworld.relicprison.admin;

import org.bukkit.configuration.file.YamlConfiguration;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.config.StorageConfig;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Safe player data export/import. Imports are intentionally restricted to offline, uncached players. */
public final class DataTransferService {
    private final RelicPrisonPlugin plugin;
    private final Path exportDirectory;

    public DataTransferService(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
        this.exportDirectory = plugin.getDataFolder().toPath().resolve("exports");
    }

    public void initialize() throws Exception { Files.createDirectories(exportDirectory); }

    public CompletableFuture<Path> exportPlayer(UUID playerId) {
        return plugin.playerProfiles().flushDirty().thenCompose(ignored -> plugin.statistics().flush())
                .thenCompose(ignored -> plugin.database().submitIdempotent(connection -> {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.set("format-version", 1);
                    yaml.set("exported-at", System.currentTimeMillis());
                    yaml.set("source-plugin-version", plugin.getPluginMeta().getVersion());
                    try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_player_profiles WHERE uuid=?")) {
                        statement.setString(1, playerId.toString());
                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next()) throw new IllegalArgumentException("No RelicPrison profile exists for " + playerId);
                            yaml.set("player.uuid", result.getString("uuid"));
                            yaml.set("player.last-name", result.getString("last_name"));
                            yaml.set("player.current-rank", result.getString("current_rank"));
                            yaml.set("player.current-prestige", result.getString("current_prestige"));
                            yaml.set("player.first-join", result.getLong("first_join"));
                            yaml.set("player.last-join", result.getLong("last_join"));
                            yaml.set("player.autosell", result.getBoolean("autosell"));
                            yaml.set("player.autopickup", result.getBoolean("autopickup"));
                            yaml.set("player.autosmelt", result.getBoolean("autosmelt"));
                            yaml.set("player.autoblock", result.getBoolean("autoblock"));
                            yaml.set("player.blocks.lifetime", result.getLong("lifetime_blocks"));
                            yaml.set("player.blocks.daily", result.getLong("daily_blocks"));
                            yaml.set("player.blocks.weekly", result.getLong("weekly_blocks"));
                            yaml.set("player.blocks.monthly", result.getLong("monthly_blocks"));
                            yaml.set("player.periods.daily", result.getString("daily_period"));
                            yaml.set("player.periods.weekly", result.getString("weekly_period"));
                            yaml.set("player.periods.monthly", result.getString("monthly_period"));
                            yaml.set("player.money-earned", result.getString("money_earned"));
                            yaml.set("player.data-version", result.getInt("data_version"));
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_player_statistics WHERE player_uuid=?")) {
                        statement.setString(1, playerId.toString());
                        try (ResultSet result = statement.executeQuery()) {
                            if (result.next()) {
                                yaml.set("statistics.items-sold", result.getLong("items_sold"));
                                yaml.set("statistics.rankups", result.getLong("rankups"));
                                yaml.set("statistics.prestiges", result.getLong("prestiges"));
                                yaml.set("statistics.boosters-used", result.getLong("boosters_used"));
                                yaml.set("statistics.playtime-seconds", result.getLong("playtime_seconds"));
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT mine_id,lifetime_blocks FROM rp_player_mine_statistics WHERE player_uuid=? ORDER BY mine_id")) {
                        statement.setString(1, playerId.toString());
                        try (ResultSet result = statement.executeQuery()) {
                            while (result.next()) yaml.set("mine-statistics." + result.getString("mine_id"), result.getLong("lifetime_blocks"));
                        }
                    }
                    String safeName = String.valueOf(yaml.getString("player.last-name", "unknown"))
                            .replaceAll("[^A-Za-z0-9_-]", "_");
                    Path output = exportDirectory.resolve(safeName + "-" + playerId + "-" + System.currentTimeMillis() + ".yml");
                    yaml.save(output.toFile());
                    return output;
                }));
    }

    public CompletableFuture<UUID> importPlayer(Path requestedFile, boolean confirmed) {
        if (!confirmed) return CompletableFuture.failedFuture(new IllegalArgumentException("Import requires confirmation"));
        Path normalized = requestedFile.isAbsolute() ? requestedFile.normalize() : exportDirectory.resolve(requestedFile).normalize();
        if (!normalized.startsWith(exportDirectory) || !Files.isRegularFile(normalized)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Import file must exist inside " + exportDirectory));
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(normalized.toFile());
        if (yaml.getInt("format-version", -1) != 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported export format"));
        }
        final UUID playerId;
        try { playerId = UUID.fromString(yaml.getString("player.uuid", "")); }
        catch (IllegalArgumentException error) { return CompletableFuture.failedFuture(new IllegalArgumentException("Export contains an invalid UUID")); }
        if (plugin.getServer().getPlayer(playerId) != null || plugin.playerProfiles().cachedProfile(playerId).isPresent()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player must be offline and unloaded before import"));
        }
        String rank = normalize(yaml.getString("player.current-rank", plugin.config().snapshot().progression().startingRank()));
        String prestige = normalizeNullable(yaml.getString("player.current-prestige"));
        if (plugin.rankService().definition(rank).isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown rank in export: " + rank));
        if (prestige != null && plugin.prestigeService().definition(prestige).isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown prestige in export: " + prestige));

        return plugin.database().submit(connection -> {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean mysql = plugin.database().storageType() == StorageConfig.Type.MYSQL;
                String profileSql = "INSERT INTO rp_player_profiles (uuid,last_name,current_rank,current_prestige,first_join,last_join," +
                        "autosell,autopickup,autosmelt,autoblock,lifetime_blocks,daily_blocks,weekly_blocks,monthly_blocks," +
                        "daily_period,weekly_period,monthly_period,money_earned,data_version,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        (mysql ? "ON DUPLICATE KEY UPDATE last_name=VALUES(last_name),current_rank=VALUES(current_rank),current_prestige=VALUES(current_prestige),first_join=VALUES(first_join),last_join=VALUES(last_join),autosell=VALUES(autosell),autopickup=VALUES(autopickup),autosmelt=VALUES(autosmelt),autoblock=VALUES(autoblock),lifetime_blocks=VALUES(lifetime_blocks),daily_blocks=VALUES(daily_blocks),weekly_blocks=VALUES(weekly_blocks),monthly_blocks=VALUES(monthly_blocks),daily_period=VALUES(daily_period),weekly_period=VALUES(weekly_period),monthly_period=VALUES(monthly_period),money_earned=VALUES(money_earned),data_version=VALUES(data_version),updated_at=VALUES(updated_at)"
                                : "ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name,current_rank=excluded.current_rank,current_prestige=excluded.current_prestige,first_join=excluded.first_join,last_join=excluded.last_join,autosell=excluded.autosell,autopickup=excluded.autopickup,autosmelt=excluded.autosmelt,autoblock=excluded.autoblock,lifetime_blocks=excluded.lifetime_blocks,daily_blocks=excluded.daily_blocks,weekly_blocks=excluded.weekly_blocks,monthly_blocks=excluded.monthly_blocks,daily_period=excluded.daily_period,weekly_period=excluded.weekly_period,monthly_period=excluded.monthly_period,money_earned=excluded.money_earned,data_version=excluded.data_version,updated_at=excluded.updated_at");
                try (PreparedStatement statement = connection.prepareStatement(profileSql)) {
                    int i = 1;
                    statement.setString(i++, playerId.toString());
                    statement.setString(i++, yaml.getString("player.last-name", "unknown"));
                    statement.setString(i++, rank);
                    statement.setString(i++, prestige);
                    statement.setLong(i++, yaml.getLong("player.first-join", System.currentTimeMillis()));
                    statement.setLong(i++, yaml.getLong("player.last-join", System.currentTimeMillis()));
                    statement.setBoolean(i++, yaml.getBoolean("player.autosell", false));
                    statement.setBoolean(i++, yaml.getBoolean("player.autopickup", true));
                    statement.setBoolean(i++, yaml.getBoolean("player.autosmelt", true));
                    statement.setBoolean(i++, yaml.getBoolean("player.autoblock", false));
                    statement.setLong(i++, Math.max(0, yaml.getLong("player.blocks.lifetime", 0)));
                    statement.setLong(i++, Math.max(0, yaml.getLong("player.blocks.daily", 0)));
                    statement.setLong(i++, Math.max(0, yaml.getLong("player.blocks.weekly", 0)));
                    statement.setLong(i++, Math.max(0, yaml.getLong("player.blocks.monthly", 0)));
                    statement.setString(i++, yaml.getString("player.periods.daily", "unknown"));
                    statement.setString(i++, yaml.getString("player.periods.weekly", "unknown"));
                    statement.setString(i++, yaml.getString("player.periods.monthly", "unknown"));
                    statement.setString(i++, new BigDecimal(yaml.getString("player.money-earned", "0")).max(BigDecimal.ZERO).toPlainString());
                    statement.setInt(i++, Math.max(1, yaml.getInt("player.data-version", 1)));
                    statement.setLong(i, System.currentTimeMillis());
                    statement.executeUpdate();
                }
                String statsSql = "INSERT INTO rp_player_statistics(player_uuid,items_sold,rankups,prestiges,boosters_used,playtime_seconds,updated_at) VALUES(?,?,?,?,?,?,?) " +
                        (mysql ? "ON DUPLICATE KEY UPDATE items_sold=VALUES(items_sold),rankups=VALUES(rankups),prestiges=VALUES(prestiges),boosters_used=VALUES(boosters_used),playtime_seconds=VALUES(playtime_seconds),updated_at=VALUES(updated_at)"
                                : "ON CONFLICT(player_uuid) DO UPDATE SET items_sold=excluded.items_sold,rankups=excluded.rankups,prestiges=excluded.prestiges,boosters_used=excluded.boosters_used,playtime_seconds=excluded.playtime_seconds,updated_at=excluded.updated_at");
                try (PreparedStatement statement = connection.prepareStatement(statsSql)) {
                    statement.setString(1, playerId.toString());
                    statement.setLong(2, Math.max(0, yaml.getLong("statistics.items-sold", 0)));
                    statement.setLong(3, Math.max(0, yaml.getLong("statistics.rankups", 0)));
                    statement.setLong(4, Math.max(0, yaml.getLong("statistics.prestiges", 0)));
                    statement.setLong(5, Math.max(0, yaml.getLong("statistics.boosters-used", 0)));
                    statement.setLong(6, Math.max(0, yaml.getLong("statistics.playtime-seconds", 0)));
                    statement.setLong(7, System.currentTimeMillis());
                    statement.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM rp_player_mine_statistics WHERE player_uuid=?")) {
                    delete.setString(1, playerId.toString()); delete.executeUpdate();
                }
                var section = yaml.getConfigurationSection("mine-statistics");
                if (section != null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO rp_player_mine_statistics(player_uuid,mine_id,lifetime_blocks,updated_at) VALUES(?,?,?,?)")) {
                        for (String mineId : section.getKeys(false)) {
                            if (plugin.mineService().findMine(mineId).isEmpty()) continue;
                            statement.setString(1, playerId.toString()); statement.setString(2, mineId.toLowerCase(Locale.ROOT));
                            statement.setLong(3, Math.max(0, section.getLong(mineId))); statement.setLong(4, System.currentTimeMillis());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                connection.commit();
                return playerId;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(previous); }
        });
    }

    public Path exportDirectory() { return exportDirectory; }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String normalizeNullable(String value) { String normalized = normalize(value); return normalized.isBlank() || normalized.equals("none") ? null : normalized; }
}
