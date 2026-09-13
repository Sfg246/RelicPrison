package site.mcrelicworld.relicprison.config;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import site.mcrelicworld.relicprison.logging.LogCategory;

import java.io.File;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private final File configFile;
    private final File storageFile;
    private final File integrationsFile;
    private volatile ConfigSnapshot snapshot;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.storageFile = new File(plugin.getDataFolder(), "storage.yml");
        this.integrationsFile = new File(plugin.getDataFolder(), "integrations.yml");
    }

    public ConfigSnapshot snapshot() {
        ConfigSnapshot current = snapshot;
        if (current == null) throw new IllegalStateException("Configuration has not been loaded");
        return current;
    }

    public ConfigSnapshot loadInitial() throws ConfigException {
        if (!configFile.exists()) plugin.saveResource("config.yml", false);
        if (!storageFile.exists()) plugin.saveResource("storage.yml", false);
        ensureIntegrationsFile();
        ConfigSnapshot loaded = parse();
        snapshot = loaded;
        return loaded;
    }

    public ConfigSnapshot preview() throws ConfigException { return parse(); }
    public void apply(ConfigSnapshot loaded) { snapshot = loaded; }
    public IntegrationConfig previewIntegrations() throws ConfigException {
        try {
            return parseIntegrations(loadYaml(configFile));
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConfigException("Unable to read config.yml while previewing integrations.yml: " + ex.getMessage(), ex);
        }
    }
    public ConfigSnapshot applyIntegrations(IntegrationConfig integrations) {
        ConfigSnapshot loaded = snapshot().withIntegrations(integrations);
        apply(loaded);
        return loaded;
    }
    public ConfigSnapshot reload() throws ConfigException {
        ConfigSnapshot loaded = preview();
        apply(loaded);
        return loaded;
    }

    private ConfigSnapshot parse() throws ConfigException {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(configFile);
            int version = positive(yaml.getInt("config-version", 1), "config-version");
            ZoneId timezone;
            try {
                timezone = ZoneId.of(yaml.getString("server.timezone", "America/Chicago"));
            } catch (DateTimeException ex) {
                throw new ConfigException("server.timezone is invalid", ex);
            }

            Set<String> excluded = new HashSet<>();
            for (String world : yaml.getStringList("server.excluded-worlds")) {
                if (!world.isBlank()) excluded.add(world.toLowerCase(Locale.ROOT));
            }

            IntegrationConfig integrations = parseIntegrations(yaml);
            FeatureToggles features = new FeatureToggles(
                    yaml.getBoolean("features.autosell", false),
                    yaml.getBoolean("features.autopickup", false),
                    yaml.getBoolean("features.autosmelt", false),
                    yaml.getBoolean("features.autoblock", false),
                    yaml.getBoolean("features.fortune", false),
                    yaml.getBoolean("features.custom-block-drops", false),
                    yaml.getBoolean("features.mining-xp", false),
                    yaml.getBoolean("features.block-events", false),
                    yaml.getBoolean("features.player-leaderboards", false),
                    yaml.getBoolean("features.player-mining-statistics", true),
                    yaml.getBoolean("features.mine-analytics", false),
                    integrations.worldGuardEnabled(),
                    integrations.worldEditFaweEnabled(),
                    integrations.itemsAdderEnabled(),
                    integrations.advancedEnchantmentsEnabled(),
                    yaml.getBoolean("features.multiple-currencies", false),
                    yaml.getBoolean("features.reset-command-hooks", true),
                    yaml.getBoolean("features.update-checker", false)
            );

            Material wand = Material.matchMaterial(yaml.getString("selection.wand-material", "BLAZE_ROD"));
            if (wand == null || !wand.isItem()) throw new ConfigException("selection.wand-material must be a valid item material");
            SelectionConfig selection = new SelectionConfig(
                    positive(yaml.getInt("selection.session-timeout-seconds", 300), "selection.session-timeout-seconds"),
                    positive(yaml.getInt("selection.preview-refresh-ticks", 20), "selection.preview-refresh-ticks"),
                    positive(yaml.getInt("selection.preview-max-particles", 600), "selection.preview-max-particles"),
                    positive(yaml.getInt("selection.preview-spacing", 2), "selection.preview-spacing"),
                    positiveLong(yaml.getLong("selection.maximum-volume", 250000), "selection.maximum-volume"),
                    yaml.getBoolean("selection.allow-overlap", false),
                    wand
            );

            StorageConfig storage = parseStorage();

            List<String> abbreviations = yaml.getStringList("formatting.abbreviations");
            if (abbreviations.isEmpty()) abbreviations = List.of("K", "M", "B", "T");
            FormattingConfig formatting = new FormattingConfig(
                    yaml.getString("formatting.currency-symbol", "$"),
                    range(yaml.getInt("formatting.decimals", 2), 0, 8, "formatting.decimals"),
                    range(yaml.getInt("formatting.abbreviated-decimals", 2), 0, 8, "formatting.abbreviated-decimals"),
                    yaml.getBoolean("formatting.use-grouping", true),
                    List.copyOf(abbreviations),
                    range(yaml.getInt("formatting.progress-bar.length", 10), 1, 100, "formatting.progress-bar.length"),
                    required(yaml.getString("formatting.progress-bar.filled", "█"), "formatting.progress-bar.filled"),
                    required(yaml.getString("formatting.progress-bar.empty", "░"), "formatting.progress-bar.empty")
            );

            PlaceholderConfig placeholders = new PlaceholderConfig(
                    range(yaml.getLong("placeholders.value-cache-millis", 250L), 50L, 10_000L,
                            "placeholders.value-cache-millis"),
                    range(yaml.getLong("placeholders.leaderboard-cache-millis", 30_000L), 100L, 300_000L,
                            "placeholders.leaderboard-cache-millis"),
                    range(yaml.getLong("placeholders.slow-threshold-millis", 10L), 1L, 1_000L,
                            "placeholders.slow-threshold-millis"),
                    yaml.getString("placeholders.unavailable-text", "loading"),
                    yaml.getString("placeholders.malformed-text", "invalid")
            );

            GuiSecurityConfig guiSecurity = new GuiSecurityConfig(
                    range(yaml.getLong("gui.security.click-cooldown-millis", 250L), 0L, 5_000L,
                            "gui.security.click-cooldown-millis"),
                    range(yaml.getLong("gui.security.session-timeout-seconds", 120L), 5L, 1800L,
                            "gui.security.session-timeout-seconds") * 1000L,
                    range(yaml.getLong("gui.security.confirmation-timeout-seconds", 15L), 5L, 300L,
                            "gui.security.confirmation-timeout-seconds") * 1000L
            );

            LeaderboardConfig leaderboards = parseLeaderboards(yaml);

            LoggingConfig logging = parseLogging(yaml);

            List<Integer> warnings = new ArrayList<>(yaml.getIntegerList("mine-resets.default-warning-seconds"));
            if (warnings.isEmpty()) warnings.addAll(List.of(30, 10, 5, 3, 2, 1));
            warnings.removeIf(value -> value == null || value < 0);
            warnings = warnings.stream().distinct().sorted(java.util.Comparator.reverseOrder()).toList();
            ResetEngineConfig reset = new ResetEngineConfig(
                    range(yaml.getDouble("mine-resets.target-time-per-tick-ms", 2.5), 0.1, 20.0, "mine-resets.target-time-per-tick-ms"),
                    range(yaml.getInt("mine-resets.initial-blocks-per-tick", 1000), 1, 100000, "mine-resets.initial-blocks-per-tick"),
                    range(yaml.getInt("mine-resets.min-blocks-per-tick", 100), 1, 100000, "mine-resets.min-blocks-per-tick"),
                    range(yaml.getInt("mine-resets.max-blocks-per-tick", 8000), 1, 1000000, "mine-resets.max-blocks-per-tick"),
                    range(yaml.getDouble("mine-resets.itemsadder.target-time-per-tick-ms", 1.0), 0.1, 20.0,
                            "mine-resets.itemsadder.target-time-per-tick-ms"),
                    range(yaml.getInt("mine-resets.itemsadder.initial-blocks-per-tick", 100), 1, 100000,
                            "mine-resets.itemsadder.initial-blocks-per-tick"),
                    range(yaml.getInt("mine-resets.itemsadder.min-blocks-per-tick", 10), 1, 100000,
                            "mine-resets.itemsadder.min-blocks-per-tick"),
                    range(yaml.getInt("mine-resets.itemsadder.max-blocks-per-tick", 1000), 1, 1000000,
                            "mine-resets.itemsadder.max-blocks-per-tick"),
                    range(yaml.getInt("mine-resets.max-concurrent-mines", 1), 1, 8, "mine-resets.max-concurrent-mines"),
                    parsePerWorldLimits(yaml),
                    yaml.getBoolean("mine-resets.empty-mine-trigger-enabled", true),
                    range(yaml.getDouble("mine-resets.pause-above-mspt", 45.0), 20.0, 200.0, "mine-resets.pause-above-mspt"),
                    range(yaml.getDouble("mine-resets.resume-below-mspt", 35.0), 10.0, 199.0, "mine-resets.resume-below-mspt"),
                    positive(yaml.getInt("mine-resets.default-interval-seconds", 900), "mine-resets.default-interval-seconds"),
                    range(yaml.getDouble("mine-resets.default-mined-percentage", 80.0), 0.0, 100.0, "mine-resets.default-mined-percentage"),
                    warnings,
                    positive(yaml.getInt("mine-resets.runtime-save-interval-seconds", 30), "mine-resets.runtime-save-interval-seconds"),
                    positive(yaml.getInt("mine-resets.failure-retry-delay-seconds", 60), "mine-resets.failure-retry-delay-seconds"),
                    yaml.getBoolean("mine-resets.notifications.chat", true),
                    yaml.getBoolean("mine-resets.notifications.title", true),
                    yaml.getBoolean("mine-resets.notifications.action-bar", true),
                    yaml.getBoolean("mine-resets.notifications.sound", true),
                    soundValue(yaml.getString("mine-resets.notifications.warning-sound", "BLOCK_NOTE_BLOCK_PLING"),
                            "mine-resets.notifications.warning-sound"),
                    soundValue(yaml.getString("mine-resets.notifications.completion-sound", "ENTITY_PLAYER_LEVELUP"),
                            "mine-resets.notifications.completion-sound"),
                    soundValue(yaml.getString("mine-resets.notifications.failure-sound", "ENTITY_VILLAGER_NO"),
                            "mine-resets.notifications.failure-sound")
            );
            if (reset.minimumBlocksPerTick() > reset.initialBlocksPerTick()
                    || reset.initialBlocksPerTick() > reset.maximumBlocksPerTick()) {
                throw new ConfigException("mine-resets block limits must satisfy min <= initial <= max");
            }
            if (reset.resumeBelowMspt() >= reset.pauseAboveMspt()) {
                throw new ConfigException("mine-resets.resume-below-mspt must be lower than pause-above-mspt");
            }

            TeleportConfig teleport = new TeleportConfig(
                    range(yaml.getInt("teleport.warmup-seconds", 3), 0, 60, "teleport.warmup-seconds"),
                    yaml.getBoolean("teleport.cancel-on-move", true),
                    yaml.getBoolean("teleport.cancel-on-damage", true),
                    yaml.getBoolean("mine-access.enforce-entry", true),
                    yaml.getBoolean("mine-access.allow-previous-mines", true),
                    yaml.getBoolean("first-join.teleport-to-starting-mine", true),
                    yaml.getStringList("first-join.commands")
            );

            ProgressionConfig progression = new ProgressionConfig(
                    required(yaml.getString("progression.starting-rank", "a"), "progression.starting-rank").toLowerCase(Locale.ROOT),
                    required(yaml.getString("progression.rank-group-format", "{rank}"), "progression.rank-group-format"),
                    required(yaml.getString("progression.prestige-group-format", "{prestige}"), "progression.prestige-group-format"),
                    yaml.getBoolean("progression.broadcast-rankups", false),
                    yaml.getBoolean("progression.broadcast-prestiges", true),
                    range(yaml.getInt("progression.prestige-confirmation-seconds", 30), 5, 300,
                            "progression.prestige-confirmation-seconds"),
                    yaml.getBoolean("progression.repair-on-join", true),
                    yaml.getBoolean("progression.notifications.title", true),
                    yaml.getBoolean("progression.notifications.sound", true),
                    soundValue(yaml.getString("progression.notifications.rankup-sound", "ENTITY_PLAYER_LEVELUP"),
                            "progression.notifications.rankup-sound"),
                    soundValue(yaml.getString("progression.notifications.prestige-sound", "UI_TOAST_CHALLENGE_COMPLETE"),
                            "progression.notifications.prestige-sound")
            );

            WorldGuardConfig worldGuard = integrations.worldGuard();

            validateAuxiliaryYaml();
            return new ConfigSnapshot(version, timezone, Set.copyOf(excluded), features, selection, storage,
                    formatting, placeholders, guiSecurity, leaderboards, logging, reset, teleport, progression,
                    worldGuard, integrations);
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConfigException("Unable to read config.yml: " + ex.getMessage(), ex);
        }
    }

    private StorageConfig parseStorage() throws Exception {
        YamlConfiguration storageYaml = new YamlConfiguration();
        storageYaml.load(storageFile);
        StorageConfig.Type type;
        try {
            type = StorageConfig.Type.valueOf(storageYaml.getString("type", "SQLITE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ConfigException("storage.yml: type must be SQLITE or MYSQL");
        }
        Path dataRoot = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path sqlite = dataRoot.resolve(storageYaml.getString("sqlite.file", "player-data.db")).normalize();
        if (!sqlite.startsWith(dataRoot)) throw new ConfigException("storage.yml: sqlite.file must remain inside the RelicPrison data folder");
        return new StorageConfig(
                type,
                sqlite,
                required(storageYaml.getString("mysql.host", "localhost"), "storage.yml: mysql.host"),
                positive(storageYaml.getInt("mysql.port", 3306), "storage.yml: mysql.port"),
                required(storageYaml.getString("mysql.database", "relicprison"), "storage.yml: mysql.database"),
                required(storageYaml.getString("mysql.username", "relicprison"), "storage.yml: mysql.username"),
                storageYaml.getString("mysql.password", ""),
                storageYaml.getString("mysql.parameters", "useSSL=false&serverTimezone=UTC"),
                range(storageYaml.getInt("mysql.pool-size", 6), 1, 32, "storage.yml: mysql.pool-size"),
                range(storageYaml.getInt("queue.capacity", 10000), 100, 1000000, "storage.yml: queue.capacity"),
                range(storageYaml.getInt("queue.retry-count", 3), 0, 20, "storage.yml: queue.retry-count"),
                range(storageYaml.getInt("queue.retry-delay-millis", 250), 1, 60000, "storage.yml: queue.retry-delay-millis"),
                range(storageYaml.getInt("player-loading.timeout-seconds", 15), 1, 300,
                        "storage.yml: player-loading.timeout-seconds"),
                positive(storageYaml.getInt("save-interval-seconds", 20), "storage.yml: save-interval-seconds"),
                positive(storageYaml.getInt("shutdown-flush-timeout-seconds", 10), "storage.yml: shutdown-flush-timeout-seconds")
        );
    }

    private void ensureIntegrationsFile() throws ConfigException {
        if (integrationsFile.exists()) return;
        try {
            YamlConfiguration oldConfig = loadYaml(configFile);
            YamlConfiguration migrated = new YamlConfiguration();
            migrated.set("file-version", 1);
            migrated.set("vault.enabled", true);
            migrated.set("vault.require-economy-provider", true);
            migrated.set("luckperms.enabled", true);
            migrated.set("worldguard.enabled", oldConfig.getBoolean("features.worldguard", true));
            migrated.set("worldguard.region-prefix", oldConfig.getString("worldguard.region-prefix", "relicmine_"));
            migrated.set("worldguard.create-regions", oldConfig.getBoolean("worldguard.create-regions", true));
            migrated.set("worldguard.update-regions", oldConfig.getBoolean("worldguard.update-regions", true));
            migrated.set("worldguard.delete-regions", oldConfig.getBoolean("worldguard.delete-regions", true));
            migrated.set("worldguard.flags.deny-block-place", oldConfig.getBoolean("worldguard.flags.deny-block-place", true));
            migrated.set("worldguard.flags.allow-block-break", oldConfig.getBoolean("worldguard.flags.allow-block-break", true));
            migrated.set("worldguard.flags.deny-explosions", oldConfig.getBoolean("worldguard.flags.deny-explosions", true));
            migrated.set("worldguard.flags.deny-fire-spread", oldConfig.getBoolean("worldguard.flags.deny-fire-spread", true));
            migrated.set("worldguard.flags.deny-fluid-flow", oldConfig.getBoolean("worldguard.flags.deny-fluid-flow", true));
            migrated.set("worldedit-fawe.enabled", oldConfig.getBoolean("features.worldedit-fawe", true));
            migrated.set("itemsadder.enabled", oldConfig.getBoolean("features.itemsadder", false));
            migrated.set("advanced-enchantments.enabled", oldConfig.getBoolean("features.advanced-enchantments", false));
            migrated.set("placeholderapi.enabled", true);
            migrated.set("geyser-floodgate.awareness-enabled", true);
            migrated.set("combat.provider", "none");
            migrated.set("combat.teleport-restriction.enabled", false);
            migrated.set("combat.teleport-restriction.bypass-permission", "relicprison.bypass.combat-teleport");
            migrated.set("combat.teleport-restriction.deny-message", "&cYou cannot teleport to mines while combat-tagged.");
            migrated.save(integrationsFile);
            plugin.getLogger().info("[RelicPrison][MIGRATION] Created integrations.yml from existing config.yml integration settings.");
        } catch (Exception ex) {
            throw new ConfigException("Unable to create integrations.yml migration: " + ex.getMessage(), ex);
        }
    }

    private IntegrationConfig parseIntegrations(YamlConfiguration oldConfig) throws ConfigException {
        try {
            if (!integrationsFile.exists()) ensureIntegrationsFile();
            YamlConfiguration yaml = loadYaml(integrationsFile);
            int fileVersion = yaml.getInt("file-version", 1);
            if (fileVersion <= 0) throw new ConfigException("integrations.yml: file-version must be greater than zero");
            WorldGuardConfig worldGuard = new WorldGuardConfig(
                    required(yaml.getString("worldguard.region-prefix",
                            oldConfig.getString("worldguard.region-prefix", "relicmine_")), "integrations.yml: worldguard.region-prefix"),
                    yaml.getBoolean("worldguard.create-regions", oldConfig.getBoolean("worldguard.create-regions", true)),
                    yaml.getBoolean("worldguard.update-regions", oldConfig.getBoolean("worldguard.update-regions", true)),
                    yaml.getBoolean("worldguard.delete-regions", oldConfig.getBoolean("worldguard.delete-regions", true)),
                    yaml.getBoolean("worldguard.flags.deny-block-place", oldConfig.getBoolean("worldguard.flags.deny-block-place", true)),
                    yaml.getBoolean("worldguard.flags.allow-block-break", oldConfig.getBoolean("worldguard.flags.allow-block-break", true)),
                    yaml.getBoolean("worldguard.flags.deny-explosions", oldConfig.getBoolean("worldguard.flags.deny-explosions", true)),
                    yaml.getBoolean("worldguard.flags.deny-fire-spread", oldConfig.getBoolean("worldguard.flags.deny-fire-spread", true)),
                    yaml.getBoolean("worldguard.flags.deny-fluid-flow", oldConfig.getBoolean("worldguard.flags.deny-fluid-flow", true))
            );
            return new IntegrationConfig(
                    yaml.getBoolean("vault.enabled", true),
                    yaml.getBoolean("vault.require-economy-provider", true),
                    yaml.getBoolean("luckperms.enabled", true),
                    yaml.getBoolean("worldguard.enabled", oldConfig.getBoolean("features.worldguard", true)),
                    yaml.getBoolean("worldedit-fawe.enabled", oldConfig.getBoolean("features.worldedit-fawe", true)),
                    yaml.getBoolean("itemsadder.enabled", oldConfig.getBoolean("features.itemsadder", false)),
                    yaml.getBoolean("advanced-enchantments.enabled", oldConfig.getBoolean("features.advanced-enchantments", false)),
                    yaml.getBoolean("placeholderapi.enabled", true),
                    yaml.getBoolean("geyser-floodgate.awareness-enabled", true),
                    required(yaml.getString("combat.provider", "none"), "integrations.yml: combat.provider"),
                    yaml.getBoolean("combat.teleport-restriction.enabled", false),
                    required(yaml.getString("combat.teleport-restriction.bypass-permission",
                            "relicprison.bypass.combat-teleport"),
                            "integrations.yml: combat.teleport-restriction.bypass-permission"),
                    yaml.getString("combat.teleport-restriction.deny-message",
                            "&cYou cannot teleport to mines while combat-tagged."),
                    worldGuard
            );
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConfigException("Unable to read integrations.yml: " + ex.getMessage(), ex);
        }
    }

    private LoggingConfig parseLogging(YamlConfiguration yaml) {
        EnumMap<LogCategory, Boolean> categories = new EnumMap<>(LogCategory.class);
        for (LogCategory category : LogCategory.values()) {
            categories.put(category, yaml.getBoolean("logging.categories." + category.configKey(), false));
        }
        return new LoggingConfig(yaml.getBoolean("logging.debug", false), categories);
    }

    private LeaderboardConfig parseLeaderboards(YamlConfiguration yaml) throws ConfigException {
        boolean seasonalEnabled = yaml.getBoolean("leaderboards.season.enabled", false);
        Instant start = instant(yaml.getString("leaderboards.season.start", "2026-01-01T00:00:00Z"),
                "leaderboards.season.start");
        Instant end = instant(yaml.getString("leaderboards.season.end", "2027-01-01T00:00:00Z"),
                "leaderboards.season.end");
        return new LeaderboardConfig(
                range(yaml.getLong("leaderboards.refresh-seconds", 60L), 1L, 3600L,
                        "leaderboards.refresh-seconds"),
                range(yaml.getInt("leaderboards.snapshot-size", 100), 10, 1000,
                        "leaderboards.snapshot-size"),
                range(yaml.getInt("leaderboards.page-size", 45), 1, 45,
                        "leaderboards.page-size"),
                seasonalEnabled,
                yaml.getString("leaderboards.season.id", "season-1"),
                start,
                end,
                range(yaml.getInt("leaderboards.rewards.retry-limit", 3), 0, 20,
                        "leaderboards.rewards.retry-limit"),
                range(yaml.getInt("leaderboards.rewards.command-limit-per-period", 128), 0, 5000,
                        "leaderboards.rewards.command-limit-per-period"),
                range(yaml.getLong("leaderboards.rewards.announcement-cooldown-seconds", 30L), 0L, 3600L,
                        "leaderboards.rewards.announcement-cooldown-seconds") * 1000L
        );
    }

    private static YamlConfiguration loadYaml(File file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    private void validateAuxiliaryYaml() throws Exception {
        for (String path : List.of(
                "ranks.yml", "prestiges.yml", "sell-prices.yml", "boosters.yml", "block-events.yml", "custom-drops.yml",
                "leaderboards.yml", "leaderboard-rewards.yml",
                "gangs.yml",
                "guis/mines.yml", "guis/progression.yml", "guis/prestige.yml", "guis/selling.yml",
                "guis/boosters.yml", "guis/statistics.yml", "guis/gangs.yml", "guis/admin.yml")) {
            File auxiliary = new File(plugin.getDataFolder(), path);
            if (!auxiliary.exists()) plugin.saveResource(path, false);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(auxiliary);
            int fileVersion = yaml.getInt("file-version", 1);
            if (fileVersion <= 0) throw new ConfigException(path + ": file-version must be greater than zero");
        }
    }

    private static int positive(int value, String path) throws ConfigException {
        if (value <= 0) throw new ConfigException(path + " must be greater than zero");
        return value;
    }

    private static long positiveLong(long value, String path) throws ConfigException {
        if (value <= 0) throw new ConfigException(path + " must be greater than zero");
        return value;
    }

    private static java.util.Map<String, Integer> parsePerWorldLimits(YamlConfiguration yaml) throws ConfigException {
        java.util.Map<String, Integer> values = new java.util.LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("mine-resets.per-world-concurrent-limits");
        if (section == null) return values;
        for (String world : section.getKeys(false)) {
            values.put(world.toLowerCase(Locale.ROOT), range(section.getInt(world), 1, 8,
                    "mine-resets.per-world-concurrent-limits." + world));
        }
        return values;
    }

    private static int range(int value, int min, int max, String path) throws ConfigException {
        if (value < min || value > max) throw new ConfigException(path + " must be between " + min + " and " + max);
        return value;
    }

    private static double range(double value, double min, double max, String path) throws ConfigException {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new ConfigException(path + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static long range(long value, long min, long max, String path) throws ConfigException {
        if (value < min || value > max) throw new ConfigException(path + " must be between " + min + " and " + max);
        return value;
    }

    private static Instant instant(String value, String path) throws ConfigException {
        try {
            return Instant.parse(required(value, path));
        } catch (DateTimeException ex) {
            throw new ConfigException(path + " must be an ISO-8601 instant such as 2026-01-01T00:00:00Z", ex);
        }
    }

    private static Sound soundValue(String value, String path) throws ConfigException {
        String normalized = required(value, path).trim().toUpperCase(Locale.ROOT);
        try {
            Object sound = Sound.class.getField(normalized).get(null);
            if (sound instanceof Sound resolved) return resolved;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Fall through to the clear configuration error below.
        }
        throw new ConfigException(path + " has an invalid value: " + value);
    }

    private static String required(String value, String path) throws ConfigException {
        if (value == null || value.isBlank()) throw new ConfigException(path + " cannot be blank");
        return value;
    }
}
