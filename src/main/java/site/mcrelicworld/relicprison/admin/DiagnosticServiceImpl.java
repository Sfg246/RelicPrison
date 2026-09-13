package site.mcrelicworld.relicprison.admin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.DiagnosticService;
import site.mcrelicworld.relicprison.api.model.BackupView;
import site.mcrelicworld.relicprison.api.model.DiagnosticSnapshot;
import site.mcrelicworld.relicprison.api.model.MineResetState;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DiagnosticServiceImpl implements DiagnosticService {
    private static final long MAX_CONFIG_BYTES = 1024L * 1024L;
    private static final long MAX_LOG_BYTES = 256L * 1024L;

    private final RelicPrisonPlugin plugin;
    private final ValidationService validation;

    public DiagnosticServiceImpl(RelicPrisonPlugin plugin, ValidationService validation) {
        this.plugin = plugin;
        this.validation = validation;
    }

    @Override public DiagnosticSnapshot snapshot() {
        return new DiagnosticSnapshot(System.currentTimeMillis(), baseValues(false));
    }

    public CompletableFuture<DiagnosticSnapshot> collect(boolean detailed) {
        Map<String, String> values = baseValues(detailed);
        if (plugin.database() == null) {
            values.put("database-health", "unavailable");
            return CompletableFuture.completedFuture(new DiagnosticSnapshot(System.currentTimeMillis(), values));
        }
        return plugin.database().health().thenCompose(health ->
                plugin.database().submitIdempotent(connection -> {
                    Map<String, String> databaseValues = new LinkedHashMap<>();
                    databaseValues.put("database-health", health.healthy() ? "ok" : "unavailable");
                    databaseValues.put("database-health-summary", health.summary());
                    databaseValues.put("database-latency-ms", String.valueOf(health.latencyMillis()));
                    databaseValues.put("database-queue", health.queueSize() + "/" + health.queueCapacity());
                    databaseValues.put("connection-pool-health", health.pool().initialized() && !health.pool().closed()
                            ? "ok" : "unavailable");
                    databaseValues.put("connection-pool-created", String.valueOf(health.pool().created()));
                    databaseValues.put("connection-pool-idle", String.valueOf(health.pool().idle()));
                    databaseValues.put("connection-pool-max", String.valueOf(health.pool().maxSize()));
                    databaseValues.putAll(databaseCounters(connection));
                    return databaseValues;
                })).handle((databaseValues, error) -> {
                    if (error == null) values.putAll(databaseValues);
                    else {
                        values.put("database-health", "unavailable");
                        values.put("database-health-summary", rootMessage(error));
                    }
                    values.put("collection-completed-at", Instant.now().toString());
                    return new DiagnosticSnapshot(System.currentTimeMillis(), values);
                });
    }

    public CompletableFuture<Path> export() {
        ValidationReport report = validation.validate();
        return collect(true).thenApplyAsync(snapshot -> {
            try {
                RedactionService redactor = new RedactionService();
                Path dir = plugin.getDataFolder().toPath().resolve("diagnostics");
                Files.createDirectories(dir);
                Path file = dir.resolve("relicprison-diagnostic-" + System.currentTimeMillis() + ".zip");
                try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(file)),
                        StandardCharsets.UTF_8)) {
                    add(zip, "diagnostic.txt", format(snapshot));
                    add(zip, "validation.txt", format(report));
                    add(zip, "summaries/mines.txt", mineSummary());
                    add(zip, "summaries/resets.txt", resetSummary());
                    add(zip, "summaries/ranks.txt", rankSummary());
                    add(zip, "summaries/prestiges.txt", prestigeSummary());
                    add(zip, "logs/recent-relicprison-errors.txt",
                            redactor.redactFile("logs/recent-relicprison-errors.txt", recentErrors()).content());
                    for (String name : configFiles()) {
                        addRedacted(zip, redactor, plugin.getDataFolder().toPath().resolve(name),
                                "config/" + name.replace('\\', '/'));
                    }
                    add(zip, "REDACTION-REPORT.txt", redactor.report().format());
                }
                return file;
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }

    private Map<String, String> baseValues(boolean detailed) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("collection-started-at", Instant.now().toString());
        values.put("detail-mode", String.valueOf(detailed));
        values.put("relicprison-version", plugin.getDescription().getVersion());
        values.put("server-version", safe(Bukkit::getVersion));
        values.put("bukkit-version", safe(Bukkit::getBukkitVersion));
        values.put("java-version", System.getProperty("java.version", "unavailable"));
        values.put("jvm", System.getProperty("java.vm.name", "unavailable") + " "
                + System.getProperty("java.vm.version", "unavailable"));
        values.put("online-players", String.valueOf(Bukkit.getOnlinePlayers().size()));
        values.put("lifecycle", String.valueOf(plugin.lifecycleState()));
        values.put("mines", String.valueOf(plugin.mineService() == null ? 0 : plugin.mineService().mines().size()));
        values.put("ranks", String.valueOf(plugin.rankService() == null ? 0 : plugin.rankService().ranks().size()));
        values.put("prestiges", String.valueOf(plugin.prestigeService() == null ? 0 : plugin.prestigeService().prestiges().size()));
        putProfileValues(values);
        putResetValues(values);
        putMiningValues(values);
        putIntegrationValues(values);
        putBoosterValues(values);
        putBackupValues(values);
        putValidationValues(values);
        if (plugin.placeholderDiagnostics() != null) values.putAll(plugin.placeholderDiagnostics());
        values.putAll(plugin.performanceMetrics().snapshot());
        if (plugin.commandDispatch() != null) {
            values.put("command-attempts", String.valueOf(plugin.commandDispatch().attemptedCommands()));
            values.put("failed-commands", String.valueOf(plugin.commandDispatch().failedCommands()));
            List<String> failures = plugin.commandDispatch().recentFailures();
            values.put("recent-failed-command-count", String.valueOf(failures.size()));
            if (detailed) {
                for (int index = 0; index < Math.min(10, failures.size()); index++) {
                    values.put("recent-failed-command." + index, failures.get(index));
                }
            }
        }
        return values;
    }

    private void putProfileValues(Map<String, String> values) {
        if (plugin.playerProfiles() == null) {
            values.put("profiles", "unavailable");
            return;
        }
        values.put("cached-profiles", String.valueOf(plugin.playerProfiles().cachedProfiles().size()));
        values.put("dirty-profiles", String.valueOf(plugin.playerProfiles().dirtyProfileCount()));
        values.put("failed-saves", String.valueOf(plugin.playerProfiles().failedSaveCount()));
        values.put("profiles-loading", String.valueOf(plugin.playerProfiles().loadingProfileCount()));
        values.put("timed-out-profile-loads", String.valueOf(plugin.playerProfiles().timedOutProfileLoadCount()));
        values.put("failed-profile-loads", String.valueOf(plugin.playerProfiles().failedProfileLoadCount()));
    }

    private void putResetValues(Map<String, String> values) {
        if (plugin.mineResets() == null) {
            values.put("resets", "unavailable");
            return;
        }
        values.put("reset-queue", String.valueOf(plugin.mineResets().queuedResetCount()));
        values.put("active-resets", String.valueOf(plugin.mineResets().activeResetCount()));
        values.put("failed-resets", String.valueOf(plugin.mineResets().runtimes().stream()
                .filter(runtime -> runtime.state() == MineResetState.State.FAILED).count()));
        values.put("slow-reset-slices", String.valueOf(plugin.mineResets().slowResetSliceCount()));
        values.put("last-reset-slice-ms", String.valueOf(plugin.mineResets().lastResetSliceMillis()));
        values.put("last-itemsadder-placement-ms", String.valueOf(plugin.mineResets().lastItemsAdderPlacementMillis()));
        values.put("reset-blocks-processed-per-tick", String.valueOf(plugin.mineResets().lastBlocksProcessedPerTick()));
        values.put("vanilla-placement-blocks-per-tick", String.valueOf(plugin.mineResets().lastVanillaBlocksProcessedPerTick()));
        values.put("itemsadder-placement-blocks-per-tick", String.valueOf(plugin.mineResets().lastItemsAdderBlocksProcessedPerTick()));
        values.put("reset-paused-for-mspt", String.valueOf(plugin.mineResets().pausedForMspt()));
    }

    private void putMiningValues(Map<String, String> values) {
        if (plugin.miningService() == null) {
            values.put("mining", "unavailable");
            return;
        }
        values.put("processed-mining-blocks", String.valueOf(plugin.miningService().processedBlocks()));
        values.put("active-mining-operations", String.valueOf(plugin.miningService().activeOperations()));
        values.put("blocks-processed-this-tick", String.valueOf(plugin.miningService().blocksProcessedThisTick()));
        if (plugin.statistics() != null) {
            values.put("statistics-pending-buckets", String.valueOf(plugin.statistics().pendingWriteBuckets()));
            values.put("leaderboard-cached-snapshots", String.valueOf(plugin.statistics().cachedLeaderboardSnapshots()));
            values.put("statistics-player-cache-size", String.valueOf(plugin.statistics().playerStatisticCacheSize()));
            values.put("statistics-dirty-player-entries", String.valueOf(plugin.statistics().dirtyPlayerStatisticEntries()));
            values.put("statistics-eviction-failures", String.valueOf(plugin.statistics().failedStatisticEvictions()));
            values.put("player-mining-statistics-enabled",
                    String.valueOf(plugin.config().snapshot().features().playerMiningStatistics()));
            values.put("mine-analytics-enabled",
                    String.valueOf(plugin.config().snapshot().features().mineAnalytics()));
        }
    }

    private void putIntegrationValues(Map<String, String> values) {
        values.put("vault-health", plugin.economy() == null ? "unavailable" : String.valueOf(plugin.economy().connected()));
        values.put("vault-provider", plugin.economy() == null ? "unavailable" : plugin.economy().providerName());
        values.put("luckperms-health", plugin.luckPerms() == null ? "unavailable" : String.valueOf(plugin.luckPerms().connected()));
        values.put("luckperms-version", plugin.luckPerms() == null ? "unavailable" : plugin.luckPerms().version());
        values.put("itemsadder-health", plugin.itemsAdder() == null ? "unavailable" : String.valueOf(plugin.itemsAdder().connected()));
        values.put("itemsadder-version", plugin.itemsAdder() == null ? "unavailable" : plugin.itemsAdder().version());
        values.put("advancedenchantments-health", plugin.advancedEnchantments() == null ? "unavailable"
                : String.valueOf(plugin.advancedEnchantments().connected()));
        values.put("advancedenchantments-version", plugin.advancedEnchantments() == null ? "unavailable"
                : plugin.advancedEnchantments().version());
        values.put("advancedenchantments-bridge", plugin.advancedEnchantments() == null ? "unavailable"
                : String.valueOf(plugin.advancedEnchantments().bulkBridgeActive()));
        values.put("placeholderapi-health", String.valueOf(plugin.placeholderRegistered()));
        values.put("worldedit-health", plugin.worldEdit() == null ? "unavailable" : String.valueOf(plugin.worldEdit().available()));
        values.put("worldedit-provider", plugin.worldEdit() == null ? "unavailable" : plugin.worldEdit().providerName());
        values.put("worldedit-version", plugin.worldEdit() == null ? "unavailable" : plugin.worldEdit().version());
        values.put("worldguard-health", plugin.worldGuard() == null ? "unavailable" : String.valueOf(plugin.worldGuard().available()));
        values.put("worldguard-version", plugin.worldGuard() == null ? "unavailable" : plugin.worldGuard().version());
        values.put("combat-tag-health", plugin.combatTags() == null ? "unavailable" : String.valueOf(plugin.combatTags().connected()));
        values.put("combat-tag-provider", plugin.combatTags() == null ? "unavailable" : plugin.combatTags().provider());
        values.put("combat-tag-version", plugin.combatTags() == null ? "unavailable" : plugin.combatTags().version());
        for (String name : List.of("Vault", "LuckPerms", "PlaceholderAPI", "ItemsAdder",
                "AdvancedEnchantments", "WorldEdit", "FastAsyncWorldEdit", "WorldGuard")) {
            Plugin dependency = Bukkit.getPluginManager().getPlugin(name);
            values.put("plugin-version." + name, dependency == null ? "unavailable" : dependency.getDescription().getVersion());
        }
    }

    private void putBoosterValues(Map<String, String> values) {
        if (plugin.boosterService() == null) {
            values.put("boosters", "unavailable");
            return;
        }
        values.put("active-personal-boosters", String.valueOf(plugin.boosterService().activePersonalBoosterCount()));
        values.put("active-server-boosters", String.valueOf(plugin.boosterService().activeServerBoosterCount()));
    }

    private void putBackupValues(Map<String, String> values) {
        if (plugin.backups() == null) {
            values.put("backup-status", "unavailable");
            return;
        }
        Collection<BackupView> backups = plugin.backups().backups();
        values.put("backup-status", backups.isEmpty() ? "none" : "available");
        values.put("backup-count", String.valueOf(backups.size()));
        backups.stream().max(Comparator.comparingLong(BackupView::createdAt)).ifPresentOrElse(
                backup -> {
                    values.put("last-successful-backup", backup.id());
                    values.put("last-successful-backup-created-at", Instant.ofEpochMilli(backup.createdAt()).toString());
                    values.put("last-successful-backup-checksum", backup.checksum());
                },
                () -> values.put("last-successful-backup", "unavailable"));
    }

    private void putValidationValues(Map<String, String> values) {
        ValidationReport report = validation.validate();
        values.put("configuration-validation-errors", String.valueOf(report.errors().size()));
        values.put("configuration-validation-warnings", String.valueOf(report.warnings().size()));
    }

    private Map<String, String> databaseCounters(java.sql.Connection connection) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("incomplete-progression-transactions", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_progression_transactions WHERE state NOT IN ('COMPLETED','FAILED','MANUAL_REVIEW')")));
        values.put("failed-progression-transactions", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_progression_transactions WHERE state IN ('FAILED','MANUAL_REVIEW')")));
        values.put("pending-offline-rewards", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_leaderboard_reward_ledger WHERE delivery_state='PENDING_OFFLINE'")));
        values.put("failed-reward-deliveries", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_leaderboard_reward_ledger WHERE delivery_state='STAFF_REVIEW'")));
        values.put("retryable-reward-deliveries", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_leaderboard_reward_ledger WHERE delivery_state='RETRY'")));
        values.put("pending-reward-components", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_reward_components WHERE state IN ('PENDING','RETRY_READY','SCHEDULED')")));
        values.put("claimed-reward-components", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_reward_components WHERE state IN ('CLAIMED','RUNNING')")));
        values.put("ambiguous-reward-components", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_reward_components WHERE state='AMBIGUOUS'")));
        values.put("expired-reward-claims", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_reward_components WHERE state IN ('CLAIMED','RUNNING') "
                        + "AND claim_expires_at>0 AND claim_expires_at<=" + System.currentTimeMillis())));
        values.put("ambiguous-progression-refunds", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_progression_transactions WHERE refund_state='REFUND_STAFF_REVIEW'")));
        values.put("incomplete-block-event-reservations", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_block_event_triggers WHERE state NOT IN ('COMMITTED','CANCELLED')")));
        values.put("block-event-reservations-missing-packages", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_block_event_triggers WHERE state NOT IN ('COMMITTED','CANCELLED') "
                        + "AND (package_id='' OR package_id IS NULL)")));
        values.put("block-event-claims-missing-packages", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_block_event_triggers WHERE package_id<>'' "
                        + "AND NOT EXISTS (SELECT 1 FROM rp_reward_packages "
                        + "WHERE rp_reward_packages.package_id=rp_block_event_triggers.package_id)")));
        values.put("block-event-duplicate-claims-prevented", String.valueOf(count(connection,
                "SELECT COALESCE(SUM(duplicate_count),0) FROM rp_block_event_triggers")));
        values.put("block-event-recovery-attempts", String.valueOf(count(connection,
                "SELECT COALESCE(SUM(recovery_attempts),0) FROM rp_block_event_triggers")));
        values.put("block-event-oldest-incomplete-age-ms", String.valueOf(count(connection,
                "SELECT CASE WHEN COUNT(*)=0 THEN 0 ELSE " + System.currentTimeMillis()
                        + "-MIN(created_at) END FROM rp_block_event_triggers "
                        + "WHERE state NOT IN ('COMMITTED','CANCELLED')")));
        values.put("incomplete-bulk-mining-transactions", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_bulk_mining_transactions WHERE state NOT IN "
                        + "('COMMITTED','ROLLED_BACK','FAILED_PERMANENT')")));
        values.put("failed-bulk-mining-rollbacks", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_bulk_mining_transactions WHERE state='FAILED_RECOVERABLE'")));
        values.put("incomplete-leaderboard-finalizations", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_leaderboard_reward_periods WHERE state<>'FINALIZED'")));
        values.put("leaderboard-missing-winner-packages", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_leaderboard_reward_ledger WHERE reward_package_id<>'' "
                        + "AND NOT EXISTS (SELECT 1 FROM rp_reward_packages "
                        + "WHERE rp_reward_packages.package_id=rp_leaderboard_reward_ledger.reward_package_id)")));
        values.put("leaderboard-legacy-periods-manual-review", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_leaderboard_reward_periods WHERE state='FAILED_RECOVERABLE'")));
        values.put("leaderboard-finalization-locks", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_leaderboard_reward_periods WHERE finalization_owner<>'' "
                        + "AND state<>'FINALIZED'")));
        values.put("leaderboard-recovery-attempts", String.valueOf(count(connection,
                "SELECT COALESCE(SUM(recovery_attempts),0) FROM rp_leaderboard_reward_periods")));
        values.put("duplicate-payment-config-warnings", plugin.leaderboardRewards() == null ? "unavailable"
                : String.valueOf(plugin.leaderboardRewards().config().warnings().size()));
        values.put("failed-reset-records", String.valueOf(count(connection,
                "SELECT COUNT(*) FROM rp_reset_failures WHERE retry_eligible=1")));
        values.put("audit-records", String.valueOf(count(connection, "SELECT COUNT(*) FROM rp_staff_audit")));
        return values;
    }

    private static long count(java.sql.Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    private List<String> configFiles() {
        return List.of("config.yml", "storage.yml", "messages.yml", "mines.yml", "ranks.yml", "prestiges.yml",
                "sell-prices.yml", "boosters.yml", "mining.yml", "custom-drops.yml", "block-events.yml",
                "leaderboards.yml", "leaderboard-rewards.yml", "backups.yml", "integrations.yml",
                "guis/mines.yml", "guis/progression.yml", "guis/prestige.yml", "guis/selling.yml",
                "guis/boosters.yml", "guis/statistics.yml", "guis/admin.yml");
    }

    private void addRedacted(ZipOutputStream zip, RedactionService redactor, Path path, String entryName)
            throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) return;
        if (Files.size(path) > MAX_CONFIG_BYTES) {
            add(zip, entryName + ".skipped.txt", "Skipped because file exceeds diagnostic export size limit.");
            return;
        }
        RedactionService.RedactedFile redacted = redactor.redactFile(entryName,
                Files.readString(path, StandardCharsets.UTF_8));
        add(zip, entryName, redacted.content());
    }

    private String recentErrors() {
        try {
            Path dataFolder = plugin.getDataFolder().toPath();
            Path serverRoot = dataFolder.getParent() == null ? null : dataFolder.getParent().getParent();
            if (serverRoot == null) return "Unavailable: server root could not be resolved.";
            Path latest = serverRoot.resolve("logs").resolve("latest.log").normalize();
            if (!Files.isRegularFile(latest) || Files.isSymbolicLink(latest)) return "Unavailable: server latest.log not found.";
            long size = Files.size(latest);
            long start = Math.max(0L, size - MAX_LOG_BYTES);
            byte[] bytes;
            try (java.io.InputStream input = Files.newInputStream(latest)) {
                long skipped = input.skip(start);
                while (skipped < start) {
                    long next = input.skip(start - skipped);
                    if (next <= 0L) break;
                    skipped += next;
                }
                bytes = input.readNBytes((int) Math.min(MAX_LOG_BYTES, size - skipped));
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = text.split("\\R");
            StringBuilder out = new StringBuilder();
            int stackLines = 0;
            for (String line : lines) {
                boolean relevant = line.contains("RelicPrison") || (stackLines > 0 && line.trim().startsWith("at "));
                if (!relevant) {
                    stackLines = 0;
                    continue;
                }
                out.append(line).append('\n');
                stackLines = line.contains("RelicPrison") ? 8 : stackLines - 1;
            }
            return out.isEmpty() ? "No recent RelicPrison lines found in latest.log." : out.toString();
        } catch (IOException | RuntimeException error) {
            return "Unavailable: " + rootMessage(error);
        }
    }

    private String mineSummary() {
        StringBuilder out = new StringBuilder();
        plugin.mineService().mines().stream().limit(200).forEach(mine -> out.append(mine.id())
                .append(" world=").append(mine.worldName())
                .append(" enabled=").append(mine.enabled())
                .append(" volume=").append(mine.volume())
                .append(" composition=").append(mine.compositionWeights())
                .append('\n'));
        return out.toString();
    }

    private String resetSummary() {
        StringBuilder out = new StringBuilder();
        plugin.mineResets().runtimes().stream().limit(200).forEach(runtime -> out.append(runtime.mineId())
                .append(" state=").append(runtime.state())
                .append(" remaining=").append(runtime.remainingBlocks())
                .append(" reset-count=").append(runtime.resetCount())
                .append(" last-duration-ms=").append(runtime.lastResetDurationMillis())
                .append('\n'));
        return out.toString();
    }

    private String rankSummary() {
        StringBuilder out = new StringBuilder();
        plugin.rankService().definitions().forEach(rank -> out.append(rank.id())
                .append(" next-cost=").append(rank.nextCost())
                .append(" mine=").append(rank.mineId())
                .append(" lp=").append(rank.luckPermsGroup())
                .append('\n'));
        return out.toString();
    }

    private String prestigeSummary() {
        StringBuilder out = new StringBuilder();
        plugin.prestigeService().definitions().forEach(prestige -> out.append(prestige.id())
                .append(" cost=").append(prestige.cost())
                .append(" mine=").append(prestige.mineId())
                .append(" lp=").append(prestige.luckPermsGroup())
                .append('\n'));
        return out.toString();
    }

    private static String format(DiagnosticSnapshot snapshot) {
        StringBuilder out = new StringBuilder();
        snapshot.values().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        return out.toString();
    }

    private static String format(ValidationReport report) {
        StringBuilder out = new StringBuilder("valid=").append(report.valid()).append('\n');
        report.errors().forEach(value -> out.append("ERROR: ").append(value).append('\n'));
        report.warnings().forEach(value -> out.append("WARN: ").append(value).append('\n'));
        report.information().forEach(value -> out.append("INFO: ").append(value).append('\n'));
        return out.toString();
    }

    private static void add(ZipOutputStream zip, String name, String value) throws IOException {
        if (!safeEntry(name)) throw new IOException("Unsafe diagnostic entry path: " + name);
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static boolean safeEntry(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("..")) return false;
        return !name.contains("\\") && !name.contains(":");
    }

    private static String safe(java.util.function.Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException error) {
            return "unavailable";
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
