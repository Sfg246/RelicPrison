package site.mcrelicworld.relicprison.startup;

import org.junit.jupiter.api.Test;
import site.mcrelicworld.relicprison.config.StartupConfig;
import site.mcrelicworld.relicprison.startup.StartupReporter.Integration;
import site.mcrelicworld.relicprison.startup.StartupReporter.IntegrationStatus;
import site.mcrelicworld.relicprison.startup.StartupReporter.StartupReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StartupReporterTest {
    private static final StartupConfig FULL = new StartupConfig(true, true, true);

    @Test
    void bannerContainsRelicPrisonBranding() {
        assertTrue(output(report("1.0.0"), FULL).contains("RELICPRISON"));
    }

    @Test
    void bannerUsesDynamicVersion() {
        assertTrue(output(report("9.8.7-test"), FULL).contains("RelicPrison 9.8.7-test"));
    }

    @Test
    void readyStatusIsExplicit() {
        assertTrue(output(report("1.0.0"), FULL).contains("Status       READY"));
    }

    @Test
    void startupDurationUsesReadableUnits() {
        assertEquals("999 ms", StartupReporter.formatDuration(999_999_999L));
        assertEquals("1.28 s", StartupReporter.formatDuration(1_284_000_000L));
        assertEquals("0 ms", StartupReporter.formatDuration(-1L));
    }

    @Test
    void optionalIntegrationStatusesRemainDistinct() {
        assertEquals(IntegrationStatus.DISABLED, StartupReporter.status(false, false, false));
        assertEquals(IntegrationStatus.NOT_INSTALLED, StartupReporter.status(true, false, false));
        assertEquals(IntegrationStatus.UNAVAILABLE, StartupReporter.status(true, true, false));
        assertEquals(IntegrationStatus.ENABLED, StartupReporter.status(true, true, true));
    }

    @Test
    void disabledBannerUsesOneConciseReadyLine() {
        List<String> lines = StartupReporter.format(report("1.0.0"), new StartupConfig(false, true, true));
        assertEquals(List.of("RelicPrison 1.0.0 READY in 1.28 s"), lines);
    }

    @Test
    void outputHasReasonableWidthAndNoReleaseCandidateVersion() {
        List<String> lines = StartupReporter.format(report("1.0.0"), FULL);
        assertTrue(lines.stream().allMatch(line -> line.length() <= 78));
        assertFalse(String.join("\n", lines).contains("rc6-stage6"));
    }

    private static StartupReport report(String version) {
        return new StartupReport(version, "Paper 1.21.10-R0.1-SNAPSHOT", "1.21.10", "25.0.4.1",
                "Linux 6.8.0", "SQLite", 33, 26, 7,
                List.of(new Integration("Vault", IntegrationStatus.ENABLED),
                        new Integration("ItemsAdder", IntegrationStatus.NOT_INSTALLED),
                        new Integration("Combat integration", IntegrationStatus.DISABLED)),
                1_284_000_000L);
    }

    private static String output(StartupReport report, StartupConfig config) {
        return String.join("\n", StartupReporter.format(report, config));
    }
}
