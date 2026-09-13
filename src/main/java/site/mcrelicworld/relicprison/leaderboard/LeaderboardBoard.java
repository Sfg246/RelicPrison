package site.mcrelicworld.relicprison.leaderboard;

import java.util.Locale;

public record LeaderboardBoard(String id, String metric, String period) {
    public LeaderboardBoard {
        id = normalizeId(id);
        metric = normalizeMetric(metric);
        period = normalizePeriod(period);
    }

    public static String normalizeId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Leaderboard board id is required");
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!normalized.matches("[a-z0-9_]{1,64}")) throw new IllegalArgumentException("Invalid leaderboard board id: " + value);
        return normalized;
    }

    public static String normalizeMetric(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "blocks", "lifetime_blocks", "daily_blocks", "weekly_blocks", "monthly_blocks" -> "blocks";
            case "rank", "highest_rank" -> "rank";
            case "prestige", "highest_prestige" -> "prestige";
            case "money", "money_earned" -> "money";
            case "items", "items_sold" -> "items_sold";
            case "rankups", "prestiges", "boosters", "booster_usage", "playtime" ->
                    normalized.equals("booster_usage") ? "boosters" : normalized;
            default -> throw new IllegalArgumentException("Unsupported leaderboard metric: " + value);
        };
    }

    public static String normalizePeriod(String value) {
        String normalized = value == null ? "lifetime" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "lifetime", "daily", "weekly", "monthly", "season", "seasonal" ->
                    normalized.equals("seasonal") ? "season" : normalized;
            default -> throw new IllegalArgumentException("Unsupported leaderboard period: " + value);
        };
    }
}
