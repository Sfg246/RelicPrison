package site.mcrelicworld.relicprison.booster;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BoosterConfigRepository {
    private final RelicPrisonPlugin plugin;
    private volatile BoosterConfig config;

    public BoosterConfigRepository(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public BoosterConfig config() {
        BoosterConfig current = config;
        if (current == null) {
            throw new IllegalStateException("Booster configuration is not loaded");
        }
        return current;
    }

    public BoosterConfig preview() throws Exception {
        return parse();
    }

    public void apply(BoosterConfig next) {
        config = next;
    }

    public void load() throws Exception {
        apply(parse());
    }

    private BoosterConfig parse() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(plugin.getDataFolder(), "boosters.yml"));
        BoosterConfig.StackingMode mode;
        try {
            mode = BoosterConfig.StackingMode.valueOf(yaml.getString("settings.stacking-mode", "MULTIPLY_ALL")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown boosters.settings.stacking-mode", ex);
        }
        BigDecimal maxFinal = decimal(yaml, "settings.maximum-final-multiplier", "100");
        BigDecimal maxBooster = decimal(yaml, "settings.maximum-booster-multiplier", "25");
        long maxDuration = yaml.getLong("settings.maximum-duration-seconds", 2_592_000L);
        boolean offline = yaml.getBoolean("settings.offline-time-continues", true);
        boolean strengthStacks = yaml.getBoolean("settings.strength-stacks", false);
        boolean durationStacks = yaml.getBoolean("settings.duration-stacks", true);
        Material material = Material.matchMaterial(yaml.getString("item.material", "NETHER_STAR"));
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Invalid booster item material");
        }
        String name = yaml.getString("item.name", "&d&lSell Booster");
        List<String> lore = yaml.getStringList("item.lore");
        int modelData = Math.max(0, yaml.getInt("item.custom-model-data", 0));
        Map<String, BigDecimal> permissions = permissionMultipliers(
                yaml.getConfigurationSection("permission-multipliers"));
        return new BoosterConfig(mode, maxFinal, maxBooster, Duration.ofSeconds(maxDuration), offline,
                strengthStacks, durationStacks, material, name, lore, modelData, permissions);
    }

    public static Map<String, BigDecimal> permissionMultipliers(ConfigurationSection section) {
        Map<String, BigDecimal> permissions = new LinkedHashMap<>();
        if (section != null) {
            collectPermissionMultipliers(section, "", permissions);
        }
        return permissions;
    }

    private static void collectPermissionMultipliers(ConfigurationSection section, String prefix,
                                                     Map<String, BigDecimal> permissions) {
        for (String key : section.getKeys(false)) {
            String permission = prefix.isEmpty() ? key : prefix + "." + key;
            Object raw = section.get(key);
            if (raw instanceof ConfigurationSection child) {
                collectPermissionMultipliers(child, permission, permissions);
                continue;
            }
            BigDecimal value = decimalValue(raw, "permission-multipliers." + permission);
            if (value.signum() <= 0) {
                throw new IllegalArgumentException("Permission multiplier must be positive: " + permission);
            }
            if (permissions.putIfAbsent(permission, value) != null) {
                throw new IllegalArgumentException("Duplicate permission multiplier: " + permission);
            }
        }
    }

    static BigDecimal decimalValue(Object raw, String path) {
        if (raw == null) {
            throw new IllegalArgumentException("Missing decimal at " + path);
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            return BigDecimal.valueOf(((Number) raw).longValue());
        }
        if (raw instanceof Float || raw instanceof Double) {
            double value = ((Number) raw).doubleValue();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Decimal must be finite at " + path);
            }
            return BigDecimal.valueOf(value);
        }
        if (raw instanceof Number number) {
            try {
                return new BigDecimal(number.toString());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid decimal at " + path + ": " + raw, ex);
            }
        }
        if (raw instanceof CharSequence characters) {
            String value = characters.toString().trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Empty decimal at " + path);
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid decimal at " + path + ": " + value, ex);
            }
        }
        throw new IllegalArgumentException("Expected a decimal at " + path + " but found "
                + raw.getClass().getSimpleName());
    }

    private static BigDecimal decimal(YamlConfiguration yaml, String path, String fallback) {
        return decimalValue(yaml.get(path, fallback), path);
    }
}
