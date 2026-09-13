package site.mcrelicworld.relicprison.gang;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.util.DurationParser;

import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class GangConfigRepository {
    private final RelicPrisonPlugin plugin;
    private volatile GangConfig config;

    public GangConfigRepository(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public GangConfig config() {
        GangConfig current = config;
        if (current == null) throw new IllegalStateException("Gang configuration is not loaded");
        return current;
    }

    public void load() throws Exception {
        apply(preview());
    }

    public void apply(GangConfig next) {
        config = next;
    }

    public GangConfig preview() throws Exception {
        File file = new File(plugin.getDataFolder(), "gangs.yml");
        if (!file.exists()) plugin.saveResource("gangs.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return parseValidated(yaml);
    }

    static GangConfig parseValidated(YamlConfiguration yaml) {
        GangConfig parsed = parse(yaml);
        validate(parsed);
        return parsed;
    }

    static GangConfig parse(YamlConfiguration yaml) {
        Map<GangConfig.ContributionType, BigDecimal> sources = new EnumMap<>(GangConfig.ContributionType.class);
        for (GangConfig.ContributionType type : GangConfig.ContributionType.values()) {
            sources.put(type, decimal(yaml, "progression.xp-sources." + type.name().toLowerCase(Locale.ROOT), "0"));
        }
        List<GangConfig.DefaultRank> ranks = parseRanks(yaml.getConfigurationSection("ranks.defaults"));
        Map<String, GangConfig.UpgradeDefinition> upgrades = parseUpgrades(yaml.getConfigurationSection("upgrades"));
        Map<String, GangConfig.MissionDefinition> missions = parseMissions(yaml.getConfigurationSection("missions.definitions"));
        return new GangConfig(
                yaml.getBoolean("enabled", true),
                decimal(yaml, "creation.cost", "0"),
                yaml.getString("validation.name.pattern", "[A-Za-z0-9 ]+"),
                yaml.getInt("validation.name.minimum", 3),
                yaml.getInt("validation.name.maximum", 24),
                yaml.getString("validation.tag.pattern", "[A-Za-z0-9]+"),
                yaml.getInt("validation.tag.minimum", 2),
                yaml.getInt("validation.tag.maximum", 6),
                yaml.getInt("members.default-limit", 10),
                yaml.getInt("members.maximum-limit", 100),
                parsePermissionLimits(yaml.getConfigurationSection("members.permission-limits")),
                duration(yaml.getString("invites.timeout", "24h")),
                decimal(yaml, "bank.capacity", "1000000000"),
                decimal(yaml, "bank.daily-withdrawal-limit", "0"),
                yaml.getInt("progression.maximum-level", 100),
                decimal(yaml, "progression.base-xp", "1000"),
                decimal(yaml, "progression.growth", "1.25"),
                sources, ranks, upgrades, missions,
                decimal(yaml, "boosters.maximum-multiplier", "5"),
                duration(yaml.getString("boosters.maximum-duration", "7d")),
                yaml.getStringList("leaderboards.categories").stream()
                        .map(GangConfigRepository::normalizeId).toList(),
                yaml.getBoolean("seasons.enabled", false),
                yaml.getString("seasons.reward-plan", ""),
                yaml.getString("messages.placeholder-unavailable", "none"));
    }

    private static List<GangConfig.DefaultRank> parseRanks(ConfigurationSection section) {
        if (section == null) return builtInRanks();
        List<GangConfig.DefaultRank> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection rank = section.getConfigurationSection(key);
            if (rank == null) continue;
            EnumSet<GangPermission> permissions = EnumSet.noneOf(GangPermission.class);
            for (String raw : rank.getStringList("permissions")) permissions.add(GangPermission.parse(raw));
            if (key.equalsIgnoreCase("owner")) permissions.addAll(GangPermission.ownerPermissions());
            result.add(new GangConfig.DefaultRank(key.toLowerCase(Locale.ROOT),
                    rank.getString("display-name", key), rank.getInt("priority"),
                    rank.getString("color", "&7"), permissions));
        }
        return result.isEmpty() ? builtInRanks() : List.copyOf(result);
    }

    private static List<GangConfig.DefaultRank> builtInRanks() {
        Set<GangPermission> leadership = EnumSet.of(GangPermission.INVITE, GangPermission.KICK,
                GangPermission.PROMOTE, GangPermission.DEMOTE, GangPermission.DEPOSIT_BANK,
                GangPermission.WITHDRAW_BANK, GangPermission.PURCHASE_UPGRADES,
                GangPermission.MANAGE_GANG_BOOSTERS, GangPermission.EDIT_IDENTITY,
                GangPermission.EDIT_PRIVACY, GangPermission.SET_GANG_HOME, GangPermission.USE_GANG_HOME,
                GangPermission.MANAGE_GANG_CHAT, GangPermission.VIEW_AUDIT_LOG);
        return List.of(
                new GangConfig.DefaultRank("owner", "Owner", 100, "&c", GangPermission.ownerPermissions()),
                new GangConfig.DefaultRank("co_leader", "Co-Leader", 90, "&6", leadership),
                new GangConfig.DefaultRank("officer", "Officer", 70, "&e", EnumSet.of(GangPermission.INVITE,
                        GangPermission.KICK, GangPermission.DEPOSIT_BANK, GangPermission.USE_GANG_HOME,
                        GangPermission.MANAGE_GANG_CHAT)),
                new GangConfig.DefaultRank("veteran", "Veteran", 50, "&a", EnumSet.of(
                        GangPermission.INVITE, GangPermission.DEPOSIT_BANK, GangPermission.USE_GANG_HOME)),
                new GangConfig.DefaultRank("member", "Member", 30, "&b", EnumSet.of(
                        GangPermission.DEPOSIT_BANK, GangPermission.USE_GANG_HOME)),
                new GangConfig.DefaultRank("recruit", "Recruit", 10, "&7", EnumSet.of(GangPermission.DEPOSIT_BANK)));
    }

    private static Map<String, GangConfig.UpgradeDefinition> parseUpgrades(ConfigurationSection section) {
        Map<String, GangConfig.UpgradeDefinition> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection value = section.getConfigurationSection(rawId);
            if (value == null || !value.getBoolean("enabled", true)) continue;
            String id = normalizeId(rawId);
            GangConfig.CostType costType = GangConfig.CostType.valueOf(
                    value.getString("cost-type", "POINTS").toUpperCase(Locale.ROOT));
            List<GangConfig.UpgradeTier> tiers = new ArrayList<>();
            for (Map<?, ?> rawTier : value.getMapList("tiers")) {
                Object rawCost = rawTier.containsKey("cost") ? rawTier.get("cost") : "0";
                BigDecimal cost = new BigDecimal(String.valueOf(rawCost));
                Map<String, BigDecimal> effects = new LinkedHashMap<>();
                Object rawEffects = rawTier.get("effects");
                if (rawEffects instanceof Map<?, ?> effectMap) {
                    effectMap.forEach((key, effect) -> effects.put(normalizeId(String.valueOf(key)),
                            new BigDecimal(String.valueOf(effect))));
                }
                tiers.add(new GangConfig.UpgradeTier(cost, effects));
            }
            List<GangConfig.UpgradePrerequisite> prerequisites = new ArrayList<>();
            for (String raw : value.getStringList("prerequisites")) {
                String[] parts = raw.split(":", 2);
                prerequisites.add(new GangConfig.UpgradePrerequisite(normalizeId(parts[0]),
                        parts.length == 1 ? 1 : Integer.parseInt(parts[1])));
            }
            result.put(id, new GangConfig.UpgradeDefinition(id, value.getString("display-name", rawId),
                    costType, tiers, prerequisites));
        }
        return Map.copyOf(result);
    }

    private static Map<String, GangConfig.MissionDefinition> parseMissions(ConfigurationSection section) {
        Map<String, GangConfig.MissionDefinition> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection value = section.getConfigurationSection(rawId);
            if (value == null || !value.getBoolean("enabled", true)) continue;
            String id = normalizeId(rawId);
            GangConfig.MissionObjective objective = GangConfig.MissionObjective.valueOf(
                    value.getString("objective", "BLOCKS").toUpperCase(Locale.ROOT));
            GangConfig.ResetPeriod period = GangConfig.ResetPeriod.valueOf(
                    value.getString("reset", "DAILY").toUpperCase(Locale.ROOT));
            ConfigurationSection reward = value.getConfigurationSection("rewards");
            GangBooster.Type boosterType = null;
            BigDecimal boosterMultiplier = BigDecimal.ONE;
            Duration boosterDuration = Duration.ZERO;
            if (reward != null && reward.isConfigurationSection("booster")) {
                ConfigurationSection booster = reward.getConfigurationSection("booster");
                boosterType = GangBooster.Type.valueOf(booster.getString("type", "GANG_XP").toUpperCase(Locale.ROOT));
                boosterMultiplier = decimal(booster, "multiplier", "1");
                boosterDuration = duration(booster.getString("duration", "0s"));
            }
            GangConfig.MissionReward missionReward = new GangConfig.MissionReward(
                    reward == null ? BigDecimal.ZERO : decimal(reward, "gang-xp", "0"),
                    reward == null ? 0 : reward.getLong("gang-points", 0),
                    reward == null ? BigDecimal.ZERO : decimal(reward, "gang-money", "0"),
                    boosterType, boosterMultiplier, boosterDuration,
                    reward == null ? List.of() : reward.getStringList("reward-components"));
            result.put(id, new GangConfig.MissionDefinition(id, value.getString("display-name", rawId), objective,
                    value.getString("target-key", ""), decimal(value, "target", "1"), period, missionReward));
        }
        return Map.copyOf(result);
    }

    private static Map<String, Integer> parsePermissionLimits(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, Integer> result = new LinkedHashMap<>();
        parsePermissionLimits(section, "", result);
        return Map.copyOf(result);
    }

    private static void parsePermissionLimits(ConfigurationSection section, String prefix,
                                              Map<String, Integer> result) {
        for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            String permission = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object rawLimit = entry.getValue();
            if (rawLimit instanceof ConfigurationSection nested) {
                parsePermissionLimits(nested, permission, result);
                continue;
            }
            int limit = rawLimit instanceof Number number ? number.intValue() : 0;
            result.put(permission, limit);
        }
    }

    private static void validate(GangConfig config) {
        if (config.creationCost().signum() < 0) throw new IllegalArgumentException("Gang creation cost cannot be negative");
        validatePattern(config.namePattern(), "name");
        validatePattern(config.tagPattern(), "tag");
        if (config.nameMinimum() < 1 || config.nameMaximum() < config.nameMinimum()) {
            throw new IllegalArgumentException("Invalid gang name length range");
        }
        if (config.tagMinimum() < 1 || config.tagMaximum() < config.tagMinimum()) {
            throw new IllegalArgumentException("Invalid gang tag length range");
        }
        if (config.defaultMemberLimit() < 1 || config.maximumMemberLimit() < config.defaultMemberLimit()) {
            throw new IllegalArgumentException("Invalid gang member limits");
        }
        for (Map.Entry<String, Integer> entry : config.memberPermissionLimits().entrySet()) {
            int limit = entry.getValue();
            if (limit < config.defaultMemberLimit() || limit > config.maximumMemberLimit()) {
                throw new IllegalArgumentException("Gang permission member limit for '" + entry.getKey()
                        + "' is " + limit + "; allowed range is [" + config.defaultMemberLimit()
                        + ", " + config.maximumMemberLimit() + "]");
            }
        }
        if (config.bankCapacity().signum() <= 0 || config.dailyWithdrawalLimit().signum() < 0) {
            throw new IllegalArgumentException("Invalid gang bank limits");
        }
        if (config.inviteTimeout().isZero() || config.inviteTimeout().isNegative()) {
            throw new IllegalArgumentException("Invite timeout must be positive");
        }
        if (config.maximumLevel() < 1 || config.levelBaseXp().signum() <= 0
                || config.levelGrowth().compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Invalid gang progression curve");
        }
        Set<String> keys = new java.util.HashSet<>();
        Set<Integer> priorities = new java.util.HashSet<>();
        for (GangConfig.DefaultRank rank : config.defaultRanks()) {
            if (!keys.add(rank.key()) || !priorities.add(rank.priority())) {
                throw new IllegalArgumentException("Default gang ranks require unique keys and priorities");
            }
        }
        if (!keys.contains("owner") || !keys.contains("recruit")) {
            throw new IllegalArgumentException("Default gang ranks must include owner and recruit");
        }
        for (GangConfig.UpgradeDefinition upgrade : config.upgrades().values()) {
            if (upgrade.tiers().isEmpty()) throw new IllegalArgumentException("Upgrade has no tiers: " + upgrade.id());
            for (GangConfig.UpgradeTier tier : upgrade.tiers()) {
                if (tier.cost().signum() < 0) throw new IllegalArgumentException("Upgrade cost cannot be negative");
            }
        }
        for (GangConfig.MissionDefinition mission : config.missions().values()) {
            if (mission.target().signum() <= 0) throw new IllegalArgumentException("Mission target must be positive");
        }
        if (config.maximumBoosterMultiplier().compareTo(BigDecimal.ONE) < 0
                || config.maximumBoosterDuration().isZero() || config.maximumBoosterDuration().isNegative()) {
            throw new IllegalArgumentException("Invalid gang booster limits");
        }
        Set<String> leaderboardCategories = Set.of("level", "xp", "blocks", "money", "prestiges",
                "balance", "block_events");
        if (config.leaderboardCategories().isEmpty()
                || config.leaderboardCategories().stream().anyMatch(value -> !leaderboardCategories.contains(value))) {
            throw new IllegalArgumentException("Invalid gang leaderboard categories");
        }
        validateRewardPlan(config.seasonRewardPlan());
    }

    private static void validateRewardPlan(String rewardPlan) {
        if (rewardPlan == null || rewardPlan.isBlank()) return;
        for (String tier : rewardPlan.split(";")) {
            String[] parts = tier.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank()) {
                throw new IllegalArgumentException("Invalid gang season reward tier: " + tier);
            }
            for (String position : parts[0].split(",")) {
                if (Integer.parseInt(position.trim()) < 1) {
                    throw new IllegalArgumentException("Gang season reward positions must be positive");
                }
            }
            GangRewardComponents.parse(List.of(parts[1].split("\\|")), "validation");
        }
    }

    private static void validatePattern(String pattern, String field) {
        try { Pattern.compile(pattern); }
        catch (PatternSyntaxException ex) { throw new IllegalArgumentException("Invalid gang " + field + " pattern", ex); }
    }

    private static BigDecimal decimal(ConfigurationSection section, String path, String fallback) {
        return new BigDecimal(section.getString(path, fallback));
    }

    private static Duration duration(String value) {
        return DurationParser.parse(value);
    }

    static String normalizeId(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (normalized.isBlank()) throw new IllegalArgumentException("Gang configuration ID cannot be blank");
        return normalized;
    }
}
