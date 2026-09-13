package site.mcrelicworld.relicprison.leaderboard;

import site.mcrelicworld.relicprison.config.LeaderboardConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class LeaderboardPeriods {
    private LeaderboardPeriods() {}

    public static String currentPeriodId(String period, long epochMillis, ZoneId timezone,
                                         LeaderboardConfig config) {
        return periodId(period, epochMillis, timezone, config, false);
    }

    public static String completedPeriodId(String period, long epochMillis, ZoneId timezone,
                                           LeaderboardConfig config) {
        return periodId(period, epochMillis, timezone, config, true);
    }

    private static String periodId(String rawPeriod, long epochMillis, ZoneId timezone, LeaderboardConfig config,
                                   boolean previous) {
        String period = LeaderboardBoard.normalizePeriod(rawPeriod);
        LocalDate date = Instant.ofEpochMilli(epochMillis).atZone(timezone).toLocalDate();
        return switch (period) {
            case "daily" -> previous ? date.minusDays(1).toString() : date.toString();
            case "weekly" -> weekly(previous ? date.minusWeeks(1) : date);
            case "monthly" -> monthly(previous ? date.minusMonths(1) : date);
            case "season" -> config.seasonId();
            default -> "lifetime";
        };
    }

    public static String periodType(String period) {
        return LeaderboardBoard.normalizePeriod(period);
    }

    private static String weekly(LocalDate date) {
        WeekFields fields = WeekFields.ISO;
        return date.get(fields.weekBasedYear()) + "-W"
                + String.format(Locale.ROOT, "%02d", date.get(fields.weekOfWeekBasedYear()));
    }

    private static String monthly(LocalDate date) {
        return date.getYear() + "-" + String.format(Locale.ROOT, "%02d", date.getMonthValue());
    }
}
