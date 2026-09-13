package site.mcrelicworld.relicprison.blockevent;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BlockEventCatalog {
    private final List<BlockEventDefinition> events;

    public BlockEventCatalog(List<BlockEventDefinition> events) {
        this.events = List.copyOf(events);
    }

    public static BlockEventCatalog load(File dataFolder) throws Exception {
        File file = new File(dataFolder, "block-events.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("events");
        if (root == null) return new BlockEventCatalog(List.of());
        List<BlockEventDefinition> result = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            result.add(new BlockEventDefinition(
                    normalize(id),
                    section.getString("display-name", id),
                    section.getInt("priority", 0),
                    trigger(section.getString("trigger", "chance-per-action")),
                    miningType(section.getString("mining-type", "both")),
                    chance(section.get("chance"), 1.0D),
                    section.getLong("cooldown-seconds", 0L) * 1000L,
                    Math.max(0, section.getLong("every-x-blocks", 0L)),
                    Math.max(0, section.getLong("daily-target", 0L)),
                    materials(section.getStringList("blocks")),
                    lowerSet(section.getStringList("mines")),
                    lowerSet(section.getStringList("custom-blocks")),
                    lower(section.getString("minimum-rank")),
                    lower(section.getString("minimum-prestige")),
                    section.getStringList("permissions").stream().filter(value -> !value.isBlank()).toList(),
                    rewards(section.getConfigurationSection("rewards")),
                    Math.max(0, section.getInt("per-action-command-limit", 16)),
                    Math.max(1, section.getInt("per-action-reward-limit", 64))
            ));
        }
        return new BlockEventCatalog(result.stream().sorted(BlockEventDefinition.ORDER).toList());
    }

    public List<BlockEventDefinition> events() { return events; }

    private static Rewards rewards(ConfigurationSection section) {
        if (section == null) return Rewards.EMPTY;
        List<ItemReward> items = new ArrayList<>();
        for (java.util.Map<?, ?> raw : section.getMapList("items")) {
            Object configuredItem = raw.containsKey("id") ? raw.get("id") : raw.get("item");
            items.add(new ItemReward(normalize(String.valueOf(configuredItem)),
                    Math.max(1, integer(raw.get("amount"), 1))));
        }
        List<String> commands = new ArrayList<>();
        for (Object raw : section.getList("commands", List.of())) commands.add(String.valueOf(raw));
        List<String> announcements = section.getStringList("announcements");
        BigDecimal money = decimal(section.get("money"), BigDecimal.ZERO);
        int experience = Math.max(0, section.getInt("xp", section.getInt("experience", 0)));
        ConfigurationSection booster = section.getConfigurationSection("booster");
        BoosterReward boosterReward = booster == null ? null : new BoosterReward(
                booster.getBoolean("server-wide", false),
                decimal(booster.get("multiplier"), BigDecimal.ONE),
                Math.max(1L, booster.getLong("duration-seconds", 60L)) * 1000L);
        return new Rewards(money, List.copyOf(items), List.copyOf(commands), boosterReward,
                List.copyOf(announcements), experience);
    }

    private static Set<Material> materials(List<String> values) {
        Set<Material> result = new HashSet<>();
        for (String value : values) {
            Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
            if (material == null) throw new IllegalArgumentException("Unknown block-events material: " + value);
            result.add(material);
        }
        return Set.copyOf(result);
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) if (value != null && !value.isBlank()) result.add(lower(value));
        return Set.copyOf(result);
    }

    private static Trigger trigger(String raw) {
        return Trigger.valueOf(raw.replace('-', '_').toUpperCase(Locale.ROOT));
    }

    private static MiningType miningType(String raw) {
        return MiningType.valueOf(raw.replace('-', '_').toUpperCase(Locale.ROOT));
    }

    private static double chance(Object value, double fallback) {
        if (value == null) return fallback;
        double raw = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
        if (!Double.isFinite(raw)) throw new IllegalArgumentException("Block Event chance must be finite");
        if (raw > 1.0D) raw /= 100.0D;
        return Math.max(0.0D, Math.min(1.0D, raw));
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value == null) return fallback;
        return new BigDecimal(String.valueOf(value));
    }

    private static String lower(String value) {
        return value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public enum Trigger { CHANCE_PER_BLOCK, CHANCE_PER_ACTION, EVERY_X_BLOCKS, FIRST_TIME, DAILY_TARGET }
    public enum MiningType { NORMAL, BULK, BOTH }

    public record BlockEventDefinition(String id, String displayName, int priority, Trigger trigger, MiningType miningType,
                                       double chance, long cooldownMillis, long everyXBlocks, long dailyTarget,
                                       Set<Material> materials, Set<String> mines, Set<String> customBlocks, String minimumRank,
                                       String minimumPrestige, List<String> permissions, Rewards rewards,
                                       int commandLimit, int rewardLimit) {
        static final Comparator<BlockEventDefinition> ORDER = Comparator
                .comparingInt(BlockEventDefinition::priority).reversed()
                .thenComparing(BlockEventDefinition::id);
    }

    public record Rewards(BigDecimal money, List<ItemReward> items, List<String> commands, BoosterReward booster,
                          List<String> announcements, int experience) {
        static final Rewards EMPTY = new Rewards(BigDecimal.ZERO, List.of(), List.of(), null, List.of(), 0);
        public Rewards(BigDecimal money, List<ItemReward> items, List<String> commands, BoosterReward booster,
                       List<String> announcements) {
            this(money, items, commands, booster, announcements, 0);
        }
    }

    public record ItemReward(String itemId, int amount) { }
    public record BoosterReward(boolean serverWide, BigDecimal multiplier, long durationMillis) { }
}
