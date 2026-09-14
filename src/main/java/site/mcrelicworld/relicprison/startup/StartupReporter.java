package site.mcrelicworld.relicprison.startup;

import site.mcrelicworld.relicprison.config.StartupConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class StartupReporter {
    private static final String BORDER = "============================================================";
    private static final int MAX_WIDTH = 78;

    private StartupReporter() {}

    public static void report(Logger logger, StartupReport report, StartupConfig config) {
        for (String line : format(report, config)) logger.info(line);
        List<String> warnings = warnings(report);
        for (String warning : warnings) logger.warning("[!] " + warning);
        if (warnings.isEmpty()) logger.info("Ready. All required systems initialized successfully.");
    }

    public static List<String> format(StartupReport report, StartupConfig config) {
        if (!config.banner()) {
            return List.of("RelicPrison " + report.version() + " READY in "
                    + formatDuration(report.startupNanos()));
        }
        List<String> lines = new ArrayList<>();
        lines.add(BORDER);
        lines.add("");
        lines.add("     ██████╗ ██████╗");
        lines.add("     ██╔══██╗██╔══██╗");
        lines.add("     ██████╔╝██████╔╝");
        lines.add("     ██╔══██╗██╔═══╝");
        lines.add("     ██║  ██║██║");
        lines.add("     ╚═╝  ╚═╝╚═╝");
        lines.add("");
        lines.add("              RELICPRISON");
        lines.add("");
        lines.add("     RelicPrison " + report.version());
        lines.add("     Prison Core for RelicWorld");
        if (config.environmentDetails()) {
            lines.add("");
            lines.add(row("Server", report.serverVersion()));
            lines.add(row("Minecraft", report.minecraftVersion()));
            lines.add(row("Java", report.javaVersion()));
            lines.add(row("OS", report.operatingSystem()));
            lines.add(row("Storage", report.storage()));
        }
        lines.add("");
        lines.add(row("Mines", Integer.toString(report.mineCount())));
        lines.add(row("Ranks", Integer.toString(report.rankCount())));
        lines.add(row("Prestiges", Integer.toString(report.prestigeCount())));
        if (config.integrationSummary()) {
            lines.add("");
            lines.add("     Integrations");
            for (Integration integration : report.integrations()) {
                lines.add(String.format(Locale.ROOT, "       %s %-24s %s", integration.status().symbol(),
                        integration.name(), integration.status().label()));
            }
        }
        lines.add("");
        lines.add(row("Status", "READY"));
        lines.add(row("Startup", formatDuration(report.startupNanos())));
        lines.add("");
        lines.add(BORDER);
        return lines.stream().map(StartupReporter::limit).toList();
    }

    public static String formatDuration(long durationNanos) {
        long millis = Math.max(0L, durationNanos) / 1_000_000L;
        if (millis < 1_000L) return millis + " ms";
        return String.format(Locale.ROOT, "%.2f s", millis / 1_000.0D);
    }

    public static IntegrationStatus status(boolean configured, boolean installed, boolean connected) {
        if (!configured) return IntegrationStatus.DISABLED;
        if (!installed) return IntegrationStatus.NOT_INSTALLED;
        return connected ? IntegrationStatus.ENABLED : IntegrationStatus.UNAVAILABLE;
    }

    private static List<String> warnings(StartupReport report) {
        return report.integrations().stream()
                .filter(integration -> integration.status() == IntegrationStatus.NOT_INSTALLED
                        || integration.status() == IntegrationStatus.UNAVAILABLE)
                .map(integration -> integration.name() + " is configured but "
                        + integration.status().label().toLowerCase(Locale.ROOT) + '.')
                .toList();
    }

    private static String row(String label, String value) {
        return String.format(Locale.ROOT, "     %-13s%s", label, value);
    }

    private static String limit(String line) {
        return line.length() <= MAX_WIDTH ? line : line.substring(0, MAX_WIDTH - 3) + "...";
    }

    public enum IntegrationStatus {
        ENABLED("✓", "ENABLED"),
        DISABLED("○", "DISABLED"),
        NOT_INSTALLED("○", "NOT INSTALLED"),
        UNAVAILABLE("!", "UNAVAILABLE");

        private final String symbol;
        private final String label;

        IntegrationStatus(String symbol, String label) {
            this.symbol = symbol;
            this.label = label;
        }

        public String symbol() {
            return symbol;
        }

        public String label() {
            return label;
        }
    }

    public record Integration(String name, IntegrationStatus status) {}

    public record StartupReport(
            String version,
            String serverVersion,
            String minecraftVersion,
            String javaVersion,
            String operatingSystem,
            String storage,
            int mineCount,
            int rankCount,
            int prestigeCount,
            List<Integration> integrations,
            long startupNanos
    ) {
        public StartupReport {
            integrations = List.copyOf(integrations);
        }
    }
}
