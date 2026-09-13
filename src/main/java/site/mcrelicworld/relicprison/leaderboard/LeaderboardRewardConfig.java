package site.mcrelicworld.relicprison.leaderboard;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import site.mcrelicworld.relicprison.util.DurationParser;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record LeaderboardRewardConfig(
        boolean enabled,
        boolean announceRewards,
        String offlineMessage,
        Map<String, BoardRewards> boards,
        List<ValidationWarning> warnings
) {
    public LeaderboardRewardConfig {
        offlineMessage = offlineMessage == null ? "" : offlineMessage;
        boards = Map.copyOf(boards);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public LeaderboardRewardConfig(boolean enabled, boolean announceRewards, String offlineMessage,
                                   Map<String, BoardRewards> boards) {
        this(enabled, announceRewards, offlineMessage, boards, List.of());
    }

    public static LeaderboardRewardConfig load(File dataFolder) throws Exception {
        File file = new File(dataFolder, "leaderboard-rewards.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        Map<String, BoardRewards> boards = new LinkedHashMap<>();
        List<ValidationWarning> warnings = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("boards");
        if (section != null) {
            for (String rawBoardId : section.getKeys(false)) {
                if (!section.getBoolean(rawBoardId + ".enabled", true)) continue;
                String boardId = LeaderboardBoard.normalizeId(rawBoardId);
                String metric = LeaderboardBoard.normalizeMetric(section.getString(rawBoardId + ".metric", boardId));
                String period = LeaderboardBoard.normalizePeriod(section.getString(rawBoardId + ".period", "lifetime"));
                List<RewardDefinition> rewards = rewards(section.getConfigurationSection(rawBoardId + ".rewards"),
                        "boards." + rawBoardId + ".rewards", boardId, warnings);
                boards.put(boardId, new BoardRewards(boardId, metric, period, rewards));
            }
        }
        return new LeaderboardRewardConfig(yaml.getBoolean("enabled", false),
                yaml.getBoolean("settings.announce-rewards", true),
                yaml.getString("settings.offline-message", ""),
                boards, warnings);
    }

    public List<RewardDefinition> rewards(String boardId, int position) {
        BoardRewards board = boards.get(LeaderboardBoard.normalizeId(boardId));
        if (board == null) return List.of();
        return board.rewards().stream().filter(reward -> reward.positions().contains(position)).toList();
    }

    private static List<RewardDefinition> rewards(ConfigurationSection section, String path, String boardId,
                                                  List<ValidationWarning> warnings) {
        if (section == null) return List.of();
        List<RewardDefinition> rewards = new ArrayList<>();
        for (String rawId : section.getKeys(false)) {
            String id = LeaderboardBoard.normalizeId(rawId);
            Set<Integer> positions = new LinkedHashSet<>();
            for (Integer position : section.getIntegerList(rawId + ".positions")) {
                if (position != null && position > 0 && position <= 1000) positions.add(position);
            }
            if (positions.isEmpty()) throw new IllegalArgumentException(path + "." + rawId + ".positions is required");
            List<ItemReward> items = items(section.get(rawId + ".items"), path + "." + rawId + ".items");
            List<BoosterReward> boosters = boosters(section.get(rawId + ".boosters"),
                    path + "." + rawId + ".boosters");
            BigDecimal money = new BigDecimal(section.getString(rawId + ".money", "0"));
            if (money.signum() < 0) throw new IllegalArgumentException(path + "." + rawId + ".money cannot be negative");
            List<String> commands = List.copyOf(section.getStringList(rawId + ".commands"));
            warnDuplicatePayment(boardId, id, money, commands, warnings);
            rewards.add(new RewardDefinition(id, Set.copyOf(positions),
                    money, commands, items, boosters,
                    List.copyOf(section.getStringList(rawId + ".announcements")),
                    Math.max(0, section.getInt(rawId + ".xp", section.getInt(rawId + ".experience", 0)))));
        }
        return List.copyOf(rewards);
    }

    private static void warnDuplicatePayment(String boardId, String rewardId, BigDecimal money, List<String> commands,
                                             List<ValidationWarning> warnings) {
        if (money == null || money.signum() <= 0) return;
        BigDecimal normalizedMoney = money.stripTrailingZeros();
        for (String command : commands) {
            BigDecimal commandAmount = economyGiveAmount(command);
            if (commandAmount == null || commandAmount.stripTrailingZeros().compareTo(normalizedMoney) != 0) {
                continue;
            }
            warnings.add(new ValidationWarning(boardId, rewardId,
                    "Native money reward and equivalent economy command are additive: " + command));
        }
    }

    private static BigDecimal economyGiveAmount(String command) {
        if (command == null) return null;
        String[] parts = command.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (parts.length < 4) return null;
        boolean economyCommand = (parts[0].equals("eco") || parts[0].equals("economy"))
                && (parts[1].equals("give") || parts[1].equals("add"));
        if (!economyCommand) return null;
        if (!parts[2].equals("%player%") && !parts[2].equals("%uuid%")) return null;
        try {
            return new BigDecimal(parts[3]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<ItemReward> items(Object raw, String path) {
        if (raw == null) return List.of();
        List<ItemReward> items = new ArrayList<>();
        if (raw instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                String itemId = section.getString(key + ".id", section.getString(key + ".material", ""));
                int amount = Math.max(1, Math.min(2304, section.getInt(key + ".amount", 1)));
                validateItemId(itemId, path + "." + key + ".id");
                items.add(new ItemReward(itemId.trim(), amount));
            }
            return List.copyOf(items);
        }
        if (raw instanceof List<?> list) {
            int index = 0;
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + "[" + index + "] must be a map");
                String itemId = mapString(map, "id", mapString(map, "material", ""));
                int amount = integer(mapValue(map, "amount", 1), path + "[" + index + "].amount");
                amount = Math.max(1, Math.min(2304, amount));
                validateItemId(itemId, path + "[" + index + "].id");
                items.add(new ItemReward(itemId.trim(), amount));
                index++;
            }
            return List.copyOf(items);
        }
        throw new IllegalArgumentException(path + " must be a section or list");
    }

    private static List<BoosterReward> boosters(Object raw, String path) {
        if (raw == null) return List.of();
        if (raw instanceof ConfigurationSection section) return boosters(section, path);
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException(path + " must be a section or list");
        List<BoosterReward> boosters = new ArrayList<>();
        int index = 0;
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + "[" + index + "] must be a map");
            String scope = mapString(map, "scope", "personal").toLowerCase(Locale.ROOT);
            boolean serverWide = switch (scope) {
                case "server", "global" -> true;
                case "personal", "player" -> false;
                default -> throw new IllegalArgumentException(path + "[" + index + "].scope must be personal or server");
            };
            BigDecimal multiplier = new BigDecimal(mapString(map, "multiplier", "1.0"));
            long durationMillis = DurationParser.parse(mapString(map, "duration", "1m")).toMillis();
            boosters.add(new BoosterReward(serverWide, multiplier, durationMillis));
            index++;
        }
        return List.copyOf(boosters);
    }

    private static List<BoosterReward> boosters(ConfigurationSection section, String path) {
        if (section == null) return List.of();
        List<BoosterReward> boosters = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            String scope = section.getString(key + ".scope", "personal").toLowerCase(Locale.ROOT);
            boolean serverWide = switch (scope) {
                case "server", "global" -> true;
                case "personal", "player" -> false;
                default -> throw new IllegalArgumentException(path + "." + key + ".scope must be personal or server");
            };
            BigDecimal multiplier = new BigDecimal(section.getString(key + ".multiplier", "1.0"));
            long durationMillis = DurationParser.parse(section.getString(key + ".duration", "1m")).toMillis();
            boosters.add(new BoosterReward(serverWide, multiplier, durationMillis));
        }
        return List.copyOf(boosters);
    }

    private static void validateItemId(String itemId, String path) {
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException(path + " is required");
        if (itemId.contains(":")) return;
        Material material = Material.matchMaterial(itemId.toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) throw new IllegalArgumentException(path + " must be an item material");
    }

    private static Object mapValue(Map<?, ?> map, String key, Object fallback) {
        return map.containsKey(key) ? map.get(key) : fallback;
    }

    private static String mapString(Map<?, ?> map, String key, String fallback) {
        Object value = mapValue(map, key, fallback);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Object value, String path) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(path + " must be an integer", ex);
        }
    }

    public record BoardRewards(String id, String metric, String period, List<RewardDefinition> rewards) {
        public BoardRewards { rewards = List.copyOf(rewards); }
    }

    public record RewardDefinition(String id, Set<Integer> positions, BigDecimal money, List<String> commands,
                                   List<ItemReward> items, List<BoosterReward> boosters,
                                   List<String> announcements, int experience) {
        public RewardDefinition {
            positions = Set.copyOf(positions);
            money = money == null ? BigDecimal.ZERO : money;
            commands = List.copyOf(commands);
            items = List.copyOf(items);
            boosters = List.copyOf(boosters);
            announcements = List.copyOf(announcements);
        }
        public RewardDefinition(String id, Set<Integer> positions, BigDecimal money, List<String> commands,
                                List<ItemReward> items, List<BoosterReward> boosters,
                                List<String> announcements) {
            this(id, positions, money, commands, items, boosters, announcements, 0);
        }
    }

    public record ItemReward(String itemId, int amount) { }

    public record BoosterReward(boolean serverWide, BigDecimal multiplier, long durationMillis) { }
    public record ValidationWarning(String boardId, String rewardId, String message) { }
}
