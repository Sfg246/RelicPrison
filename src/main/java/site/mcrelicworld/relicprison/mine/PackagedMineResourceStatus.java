package site.mcrelicworld.relicprison.mine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates the bundled fresh-install mines.yml resource without inventing missing mine data. */
public record PackagedMineResourceStatus(boolean present, int mineCount, boolean approvedThirtyThreeMineAsset,
                                         List<String> errors, List<String> warnings) {
    public static PackagedMineResourceStatus inspect(InputStream stream) {
        if (stream == null) {
            return new PackagedMineResourceStatus(false, 0, false, List.of("Packaged mines.yml is missing."), List.of());
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (Exception ex) {
            errors.add("Packaged mines.yml cannot be parsed: " + ex.getMessage());
            return new PackagedMineResourceStatus(true, 0, false, List.copyOf(errors), List.copyOf(warnings));
        }
        ConfigurationSection mines = yaml.getConfigurationSection("mines");
        if (mines == null || mines.getKeys(false).isEmpty()) {
            errors.add("Approved corrected 33-mine source asset is not packaged; mines.yml contains zero mines.");
            return new PackagedMineResourceStatus(true, 0, false, List.copyOf(errors), List.copyOf(warnings));
        }
        Set<String> seen = new HashSet<>();
        for (String id : mines.getKeys(false)) {
            if (!seen.add(id.toLowerCase(java.util.Locale.ROOT))) errors.add("Duplicate mine ID: " + id);
            ConfigurationSection mine = mines.getConfigurationSection(id);
            if (mine == null) {
                errors.add("Mine " + id + " is not a configuration section.");
                continue;
            }
            require(mine, "world.name", id, errors);
            require(mine, "world.uuid", id, errors);
            require(mine, "minimum", id, errors);
            require(mine, "maximum", id, errors);
            require(mine, "composition", id, errors);
            require(mine, "reset", id, warnings);
        }
        int count = mines.getKeys(false).size();
        if (count != 33) {
            errors.add("Packaged mines.yml has " + count + " mines; approved fresh-install asset must contain exactly 33.");
        }
        return new PackagedMineResourceStatus(true, count, count == 33 && errors.isEmpty(),
                List.copyOf(errors), List.copyOf(warnings));
    }

    public boolean missingApprovedAsset() {
        return !approvedThirtyThreeMineAsset;
    }

    private static void require(ConfigurationSection section, String path, String mineId, List<String> output) {
        if (!section.contains(path)) output.add("Mine " + mineId + " missing required field " + path + ".");
    }
}
