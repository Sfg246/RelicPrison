package site.mcrelicworld.relicprison.config;

import java.time.Instant;

public record LeaderboardConfig(
        long refreshSeconds,
        int snapshotSize,
        int pageSize,
        boolean seasonalEnabled,
        String seasonId,
        Instant seasonStart,
        Instant seasonEnd,
        int rewardRetryLimit,
        int rewardCommandLimit,
        long announcementCooldownMillis
) {
    public LeaderboardConfig {
        if (refreshSeconds < 1L || snapshotSize < 10 || pageSize < 1 || rewardRetryLimit < 0
                || rewardCommandLimit < 0 || announcementCooldownMillis < 0L) {
            throw new IllegalArgumentException("Leaderboard limits must be positive");
        }
        seasonId = seasonId == null || seasonId.isBlank() ? "default" : seasonId.trim().toLowerCase(java.util.Locale.ROOT);
        if (seasonalEnabled && (seasonStart == null || seasonEnd == null || !seasonEnd.isAfter(seasonStart))) {
            throw new IllegalArgumentException("Enabled seasonal leaderboards require a valid start and end");
        }
    }
}
