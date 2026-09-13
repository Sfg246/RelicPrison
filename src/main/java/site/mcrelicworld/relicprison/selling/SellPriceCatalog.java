package site.mcrelicworld.relicprison.selling;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.io.File;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable precompiled vanilla and ItemsAdder sell-price catalog. */
public final class SellPriceCatalog {
    private final Map<Material, BigDecimal> prices;
    private final Map<String, BigDecimal> customPrices;
    private final boolean allowItemsWithLore;
    private final boolean allowCustomItemsWithLore;
    private final BigDecimal minimumPayout;
    private final int roundingScale;

    public SellPriceCatalog(Map<Material, BigDecimal> prices, Map<String, BigDecimal> customPrices,
                            boolean allowItemsWithLore, boolean allowCustomItemsWithLore,
                            BigDecimal minimumPayout, int roundingScale) {
        EnumMap<Material, BigDecimal> copy = new EnumMap<>(Material.class);
        copy.putAll(prices);
        this.prices = Collections.unmodifiableMap(copy);
        this.customPrices = Collections.unmodifiableMap(new LinkedHashMap<>(customPrices));
        this.allowItemsWithLore = allowItemsWithLore;
        this.allowCustomItemsWithLore = allowCustomItemsWithLore;
        this.minimumPayout = minimumPayout;
        this.roundingScale = roundingScale;
    }

    public Optional<BigDecimal> price(Material material) { return Optional.ofNullable(prices.get(material)); }
    public Optional<BigDecimal> customPrice(String namespacedId) {
        return namespacedId == null ? Optional.empty() : Optional.ofNullable(customPrices.get(namespacedId.toLowerCase(Locale.ROOT)));
    }

    public Map<Material, BigDecimal> prices() { return prices; }
    public Map<String, BigDecimal> customPrices() { return customPrices; }
    public boolean allowItemsWithLore() { return allowItemsWithLore; }
    public boolean allowCustomItemsWithLore() { return allowCustomItemsWithLore; }
    public BigDecimal minimumPayout() { return minimumPayout; }
    public int roundingScale() { return roundingScale; }

    public static SellPriceCatalog load(RelicPrisonPlugin plugin) throws Exception {
        Objects.requireNonNull(plugin);
        File file = new File(plugin.getDataFolder(), "sell-prices.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        ConfigurationSection section = yaml.getConfigurationSection("prices");
        if (section == null) throw new IllegalArgumentException("sell-prices.yml is missing prices");
        EnumMap<Material, BigDecimal> values = new EnumMap<>(Material.class);
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (material == null || !material.isItem()) throw new IllegalArgumentException("Unknown sell material: " + key);
            values.put(material, parsePrice(section.get(key), key));
        }
        Map<String, BigDecimal> custom = new LinkedHashMap<>();
        ConfigurationSection customSection = yaml.getConfigurationSection("custom-prices");
        if (customSection != null) {
            flattenCustomPrices(customSection, "", custom);
        }
        boolean allowLore = yaml.getBoolean("settings.allow-items-with-lore", false);
        boolean allowCustomLore = yaml.getBoolean("settings.allow-custom-items-with-lore", true);
        BigDecimal minimum = parsePrice(yaml.get("settings.minimum-payout", "0.01"), "settings.minimum-payout");
        int scale = Math.max(0, Math.min(8, yaml.getInt("settings.rounding-scale", 2)));
        return new SellPriceCatalog(values, custom, allowLore, allowCustomLore, minimum, scale);
    }

    private static void flattenCustomPrices(ConfigurationSection section, String prefix, Map<String, BigDecimal> output) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                flattenCustomPrices(nested, path, output);
                continue;
            }
            String reconstructed = path.replace('.', ':').toLowerCase(Locale.ROOT);
            int firstColon = reconstructed.indexOf(':');
            if (firstColon < 1 || reconstructed.indexOf(':', firstColon + 1) >= 0) {
                throw new IllegalArgumentException("Custom sell key must be namespace:id: " + path);
            }
            output.put(reconstructed, parsePrice(value, "custom-prices." + path));
        }
    }

    private static BigDecimal parsePrice(Object raw, String path) {
        BigDecimal price;
        try { price = new BigDecimal(String.valueOf(raw)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid price for " + path, ex); }
        if (price.signum() < 0) throw new IllegalArgumentException("Sell price cannot be negative: " + path);
        return price.stripTrailingZeros();
    }
}
