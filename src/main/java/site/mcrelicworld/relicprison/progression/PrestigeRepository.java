package site.mcrelicworld.relicprison.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PrestigeRepository {
    private final JavaPlugin plugin;
    private final File file;

    public PrestigeRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "prestiges.yml");
    }

    public List<PrestigeDefinition> load(String groupFormat) throws Exception {
        if (!file.exists()) plugin.saveResource("prestiges.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("prestiges");
        if (root == null || root.getKeys(false).isEmpty()) throw new IllegalArgumentException("prestiges.yml has no prestiges");
        List<PrestigeDefinition> result = new ArrayList<>();
        int naturalOrder = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) throw new IllegalArgumentException("prestiges." + key + " must be a section");
            String id = PrestigeDefinition.normalize(key);
            int order = section.contains("order") ? section.getInt("order") : naturalOrder;
            result.add(new PrestigeDefinition(id, section.getString("display-name", key), order,
                    decimal(section.get("cost"), "prestiges." + key + ".cost"),
                    decimal(section.get("sell-multiplier"), "prestiges." + key + ".sell-multiplier"),
                    decimal(section.get("rank-cost-multiplier"), "prestiges." + key + ".rank-cost-multiplier"),
                    blankToNull(section.getString("mine")), section.getString("luckperms-group",
                            formatGroup(groupFormat, "{prestige}", id)),
                    section.getStringList("commands.enter"), section.getStringList("commands.leave"),
                    section.getBoolean("enabled", true), blankToNull(section.getString("permission")),
                    material(section.getString("display-material", "NETHER_STAR"), "prestiges." + key + ".display-material"),
                    section.getStringList("lore"), section.getStringList("requirements")));
            naturalOrder++;
        }
        result.sort(Comparator.comparingInt(PrestigeDefinition::order));
        Set<String> ids = new HashSet<>();
        int previous = -1;
        for (PrestigeDefinition prestige : result) {
            if (!ids.add(prestige.id())) throw new IllegalArgumentException("Duplicate prestige: " + prestige.id());
            if (prestige.order() <= previous) throw new IllegalArgumentException("Prestige orders must be unique and increasing");
            previous = prestige.order();
        }
        if (result.stream().noneMatch(PrestigeDefinition::enabled)) {
            throw new IllegalArgumentException("At least one prestige must be enabled");
        }
        return List.copyOf(result);
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
