package site.mcrelicworld.relicprison.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RankRepository {
    private final JavaPlugin plugin;
    private final File file;

    public RankRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ranks.yml");
    }

    public List<RankDefinition> load(String groupFormat) throws Exception {
        if (!file.exists()) plugin.saveResource("ranks.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("ranks");
        if (root == null || root.getKeys(false).isEmpty()) throw new IllegalArgumentException("ranks.yml has no ranks");
        List<RankDefinition> result = new ArrayList<>();
        int naturalOrder = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) throw new IllegalArgumentException("ranks." + key + " must be a section");
            String id = RankDefinition.normalize(key);
            int order = section.contains("order") ? section.getInt("order") : naturalOrder;
            result.add(new RankDefinition(id, section.getString("display-name", key.toUpperCase(java.util.Locale.ROOT)), order,
                    decimal(section.get("next-cost"), "ranks." + key + ".next-cost"),
                    decimal(section.get("sell-multiplier", "1.0"), "ranks." + key + ".sell-multiplier"),
                    blankToNull(section.getString("mine")),
                    section.getString("luckperms-group", formatGroup(groupFormat, "{rank}", id)),
                    section.getStringList("commands.enter"), section.getStringList("commands.leave"),
                    section.getBoolean("enabled", true), blankToNull(section.getString("permission")),
                    material(section.getString("display-material", "EXPERIENCE_BOTTLE"), "ranks." + key + ".display-material"),
                    section.getStringList("lore"), section.getStringList("requirements")));
            naturalOrder++;
        }
        result.sort(Comparator.comparingInt(RankDefinition::order));
        validate(result);
        return List.copyOf(result);
    }

    private static void validate(List<RankDefinition> ranks) {
        Map<String, RankDefinition> ids = new LinkedHashMap<>();
        int previousOrder = -1;
        for (RankDefinition rank : ranks) {
            if (ids.put(rank.id(), rank) != null) throw new IllegalArgumentException("Duplicate rank: " + rank.id());
            if (rank.order() <= previousOrder) throw new IllegalArgumentException("Rank orders must be unique and increasing");
            previousOrder = rank.order();
        }
        List<RankDefinition> enabled = ranks.stream().filter(RankDefinition::enabled).toList();
        if (enabled.size() < 2) throw new IllegalArgumentException("At least two enabled ranks are required");
        if (enabled.getLast().nextCost().signum() != 0) {
            throw new IllegalArgumentException("The final rank must have next-cost 0");
        }
    }

    private static String formatGroup(String format, String token, String id) {
        String resolved = format == null || format.isBlank() ? token : format;
        return resolved.replace(token, id).replace("{id}", id);
    }

    private static BigDecimal decimal(Object value, String path) {
        if (value == null) throw new IllegalArgumentException(path + " is required");
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(path + " must be a number", ex); }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static Material material(String value, String path) {
        Material material = Material.matchMaterial(value);
        if (material == null || !material.isItem()) throw new IllegalArgumentException(path + " must be an item material");
        return material;
    }
}
