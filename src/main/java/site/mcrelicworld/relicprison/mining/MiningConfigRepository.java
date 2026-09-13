package site.mcrelicworld.relicprison.mining;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MiningConfigRepository {
    private final RelicPrisonPlugin plugin;
    private volatile MiningConfig config;
    public MiningConfigRepository(RelicPrisonPlugin plugin) { this.plugin = plugin; }
    public MiningConfig config() {
        MiningConfig current = config;
        if (current == null) throw new IllegalStateException("Mining configuration is not loaded");
        return current;
    }
    public MiningConfig preview() throws Exception { return parse(); }
    public void apply(MiningConfig next) { config = next; }
    public void load() throws Exception { apply(parse()); }

    private MiningConfig parse() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(plugin.getDataFolder(), "mining.yml"));
        MiningConfig.OverflowMode overflow = enumValue(MiningConfig.OverflowMode.class,
                yaml.getString("inventory-overflow.mode", "DROP"), "inventory-overflow.mode");
        MiningConfig.BulkDurabilityMode durability = enumValue(MiningConfig.BulkDurabilityMode.class,
                yaml.getString("tool-durability.bulk-mode", "ONE_PER_OPERATION"), "tool-durability.bulk-mode");
        MiningConfig.ExperienceMultiplierMode xpMode = enumValue(MiningConfig.ExperienceMultiplierMode.class,
                yaml.getString("mining-xp.multiplier-mode", "MULTIPLICATIVE"), "mining-xp.multiplier-mode");
        return new MiningConfig(
                yaml.getBoolean("toggles.autopickup", true),
                yaml.getBoolean("toggles.autosmelt", true),
                yaml.getBoolean("toggles.autoblock", true),
                overflow,
                Math.max(1, yaml.getInt("autosell.summary-seconds", 5)),
                Math.max(1, yaml.getInt("bulk-limits.max-blocks-per-operation", 5000)),
                Math.max(1, yaml.getInt("bulk-limits.max-operations-per-player-per-second", 10)),
                Math.max(1, yaml.getInt("bulk-limits.max-global-blocks-per-tick", 20000)),
                materialMap(yaml.getConfigurationSection("autosmelt.conversions")),
                materialMap(yaml.getConfigurationSection("autoblock.conversions")),
                materialSet(yaml.getStringList("fortune.excluded-materials")),
                Math.max(0, yaml.getDouble("fortune.level-multiplier", 1.0)),
                Math.max(0, yaml.getInt("fortune.maximum-level", 1000)),
                Math.max(0, yaml.getInt("tool-durability.minimum-remaining", 1)),
                Math.max(0, yaml.getInt("tool-durability.warning-remaining", 25)),
                durability,
                Math.max(1, yaml.getInt("tool-durability.bulk-cap", 10)),
                integerMaterialMap(yaml.getConfigurationSection("mining-xp.materials")),
                integerStringMap(yaml.getConfigurationSection("mining-xp.mines")),
                doubleStringMap(yaml.getConfigurationSection("mining-xp.rank-multipliers")),
                doubleStringMap(yaml.getConfigurationSection("mining-xp.prestige-multipliers")),
                doubleStringMap(yaml.getConfigurationSection("mining-xp.permission-multipliers")),
                rangeDouble(yaml.getDouble("mining-xp.bulk-scaling", 1.0), 0.0, 1000.0,
                        "mining-xp.bulk-scaling"),
                Math.max(0, yaml.getInt("mining-xp.per-operation-cap", 5000)),
                xpMode,
                yaml.getBoolean("autoblock.use-inventory", false)
        );
    }

    private static Map<Material, Material> materialMap(ConfigurationSection section) {
        EnumMap<Material, Material> values = new EnumMap<>(Material.class);
        if (section == null) return values;
        for (String key : section.getKeys(false)) {
            Material from = material(key);
            Material to = material(section.getString(key));
            values.put(from, to);
        }
        return values;
    }

    private static Map<Material, Integer> integerMaterialMap(ConfigurationSection section) {
        EnumMap<Material, Integer> values = new EnumMap<>(Material.class);
        if (section == null) return values;
        for (String key : section.getKeys(false)) {
            int value = section.getInt(key);
            if (value < 0) throw new IllegalArgumentException("Mining XP cannot be negative: " + key);
            values.put(material(key), value);
        }
        return values;
    }

    private static Set<Material> materialSet(java.util.List<String> raw) {
        EnumSet<Material> values = EnumSet.noneOf(Material.class);
        for (String value : raw) values.add(material(value));
        return values;
    }

    private static Map<String, Integer> integerStringMap(ConfigurationSection section) {
        java.util.LinkedHashMap<String, Integer> values = new java.util.LinkedHashMap<>();
        if (section == null) return values;
        for (String key : section.getKeys(false)) {
            int value = section.getInt(key);
            if (value < 0) throw new IllegalArgumentException("Mining XP cannot be negative: " + key);
            values.put(key.toLowerCase(Locale.ROOT), value);
        }
        return values;
    }

    private static Map<String, Double> doubleStringMap(ConfigurationSection section) {
        java.util.LinkedHashMap<String, Double> values = new java.util.LinkedHashMap<>();
        if (section == null) return values;
        for (String key : section.getKeys(false)) {
            double value = section.getDouble(key);
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Mining XP multiplier cannot be negative: " + key);
            }
            values.put(key.toLowerCase(Locale.ROOT), value);
        }
        return values;
    }

    private static double rangeDouble(double value, double min, double max, String path) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(path + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static Material material(String raw) {
        Material material = raw == null ? null : Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        if (material == null) throw new IllegalArgumentException("Unknown material: " + raw);
        return material;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, String path) {
        try { return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid " + path + ": " + raw, ex); }
    }
}
