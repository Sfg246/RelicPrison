package site.mcrelicworld.relicprison.leaderboard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LeaderboardCatalog {
    private final Map<String, LeaderboardBoard> boards;

    public LeaderboardCatalog(Map<String, LeaderboardBoard> boards) {
        this.boards = Map.copyOf(boards);
    }

    public static LeaderboardCatalog load(File dataFolder) throws Exception {
        File file = new File(dataFolder, "leaderboards.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        Map<String, LeaderboardBoard> parsed = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("boards");
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                if (!section.getBoolean(rawId + ".enabled", true)) continue;
                String id = LeaderboardBoard.normalizeId(rawId);
                String metric = section.getString(rawId + ".metric", id);
                String period = section.getString(rawId + ".period", periodFromId(id));
                parsed.put(id, new LeaderboardBoard(id, metric, period));
            }
        }
        if (parsed.isEmpty()) {
            parsed.put("lifetime_blocks", new LeaderboardBoard("lifetime_blocks", "blocks", "lifetime"));
            parsed.put("daily_blocks", new LeaderboardBoard("daily_blocks", "blocks", "daily"));
        }
        return new LeaderboardCatalog(parsed);
    }

    public Optional<LeaderboardBoard> board(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return Optional.ofNullable(boards.get(normalized));
    }

    public List<LeaderboardBoard> boards() {
        return List.copyOf(boards.values());
    }

    private static String periodFromId(String id) {
        if (id.startsWith("daily_")) return "daily";
        if (id.startsWith("weekly_")) return "weekly";
        if (id.startsWith("monthly_")) return "monthly";
        if (id.startsWith("seasonal_")) return "season";
        return "lifetime";
    }
}
