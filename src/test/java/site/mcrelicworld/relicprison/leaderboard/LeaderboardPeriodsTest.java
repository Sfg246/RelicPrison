package site.mcrelicworld.relicprison.leaderboard;

import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.config.LeaderboardConfig;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LeaderboardPeriodsTest {
    private static final LeaderboardConfig CONFIG = new LeaderboardConfig(60, 100, 45, true,
            "season-one", Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2027-01-01T00:00:00Z"), 3, 128, 30_000);

    @Test
    void currentPeriodsUseConfiguredTimezoneAndIsoWeeks() {
        long instant = Instant.parse("2026-03-09T05:30:00Z").toEpochMilli();
        ZoneId chicago = ZoneId.of("America/Chicago");

        assertEquals("2026-03-09", LeaderboardPeriods.currentPeriodId("daily", instant, chicago, CONFIG));
        assertEquals("2026-W11", LeaderboardPeriods.currentPeriodId("weekly", instant, chicago, CONFIG));
        assertEquals("2026-03", LeaderboardPeriods.currentPeriodId("monthly", instant, chicago, CONFIG));
        assertEquals("season-one", LeaderboardPeriods.currentPeriodId("season", instant, chicago, CONFIG));
    }

    @Test
    void completedPeriodSelectsPreviousPeriodAfterDowntime() {
        long instant = Instant.parse("2026-08-01T18:00:00Z").toEpochMilli();

        assertEquals("2026-07-31", LeaderboardPeriods.completedPeriodId("daily", instant,
                ZoneId.of("UTC"), CONFIG));
        assertEquals("2026-W30", LeaderboardPeriods.completedPeriodId("weekly", instant,
                ZoneId.of("UTC"), CONFIG));
        assertEquals("2026-07", LeaderboardPeriods.completedPeriodId("monthly", instant,
                ZoneId.of("UTC"), CONFIG));
    }
}
