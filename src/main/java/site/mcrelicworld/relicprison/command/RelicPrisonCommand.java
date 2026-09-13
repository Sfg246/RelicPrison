package site.mcrelicworld.relicprison.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.admin.AuditEntry;
import site.mcrelicworld.relicprison.admin.AuditQuery;
import site.mcrelicworld.relicprison.admin.BackupVerificationReport;
import site.mcrelicworld.relicprison.admin.ValidationReport;
import site.mcrelicworld.relicprison.api.model.BackupView;
import site.mcrelicworld.relicprison.config.ReloadValidationException;
import site.mcrelicworld.relicprison.gui.PrisonGuiManager;
import site.mcrelicworld.relicprison.progression.ProgressionTransactionRecord;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RelicPrisonCommand implements CommandExecutor, TabCompleter {
    private final RelicPrisonPlugin plugin;

    public RelicPrisonCommand(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("status")) { status(sender); return true; }
        if (sub.equals("menu")) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "player-only");
                return true;
            }
            if (plugin.ensureProfileReady(player)) plugin.prisonGuis().open(player, PrisonGuiManager.Menu.MAIN);
            return true;
        }
        if (!sender.hasPermission("relicprison.admin")) { plugin.messages().send(sender, "no-permission"); return true; }
        if (!sub.equals("reload") && !plugin.ensureReady(sender)) return true;
        switch (sub) {
            case "admin" -> admin(sender);
            case "reload" -> reload(sender, args);
            case "validate" -> validate(sender);
            case "diagnose" -> diagnose(sender, args);
            case "diagnostic", "diagnostics" -> diagnostic(sender);
            case "backup", "backups" -> backup(sender, args);
            case "audit" -> auditList(sender, args);
            case "repair" -> repair(sender, args);
            case "progression", "progressiontx", "tx" -> progressionTransaction(sender, args);
            case "leaderboardrewards", "lbrewards", "rewards" -> leaderboardRewards(sender, args);
            case "export" -> exportPlayer(sender, args);
            case "import" -> importPlayer(sender, args);
            case "help" -> usage(sender, label, page(args, 1));
            default -> {
                plugin.messages().error(sender, "Unknown RelicPrison subcommand &f" + args[0] + "&c.");
                usage(sender, label);
            }
        }
        return true;
    }

    private void status(CommandSender sender) {
        plugin.messages().sectionHeader(sender, "RelicPrison " + plugin.getDescription().getVersion(), "&b");
        plugin.messages().field(sender, "Lifecycle", "&b", plugin.lifecycleState());
        plugin.messages().field(sender, "Mines / Ranks / Prestiges", "&f",
                (plugin.mineService() == null ? 0 : plugin.mineService().mines().size())
                + " / " + (plugin.rankService() == null ? 0 : plugin.rankService().ranks().size())
                + " / " + (plugin.prestigeService() == null ? 0 : plugin.prestigeService().prestiges().size()));
        plugin.messages().field(sender, "Active / Queued Resets", "&f",
                (plugin.mineResets() == null ? 0 : plugin.mineResets().activeResetCount()) + " / "
                + (plugin.mineResets() == null ? 0 : plugin.mineResets().queuedResetCount()));
        plugin.messages().field(sender, "Profiles / Database Queue", "&f",
                (plugin.playerProfiles() == null ? 0 : plugin.playerProfiles().cachedProfiles().size()) + " / "
                + (plugin.database() == null ? 0 : plugin.database().queueSize()));
        plugin.messages().field(sender, "WorldEdit / WorldGuard", "&f",
                health(plugin.worldEdit() != null && plugin.worldEdit().available()) + " &8/ "
                        + health(plugin.worldGuard() != null && plugin.worldGuard().available()));
        plugin.messages().field(sender, "AdvancedEnchantments / ItemsAdder / PlaceholderAPI", "&f",
                health(plugin.advancedEnchantments() != null && plugin.advancedEnchantments().connected()) + " &8/ "
                        + health(plugin.itemsAdder() != null && plugin.itemsAdder().connected()) + " &8/ "
                        + health(plugin.placeholderRegistered()));
        plugin.messages().field(sender, "Processed Mining Blocks", "&b",
                plugin.miningService() == null ? 0 : plugin.numbers().full(plugin.miningService().processedBlocks()));
        plugin.messages().sectionFooter(sender);
    }

    private void admin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        plugin.prisonGuis().open(player, PrisonGuiManager.Menu.ADMIN);
    }

    private void reload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("relicprison.admin.reload")) { plugin.messages().send(sender, "no-permission"); return; }
        String module = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
        try {
            plugin.reloadModule(module);
            audit(sender, "config_reload", "module", module, "", "reload applied", true,
                    "command", "", "label=" + sender.getName());
            plugin.messages().send(sender, "reloaded", Map.of("module", module));
        } catch (ReloadValidationException ex) {
            audit(sender, "config_reload", "module", module, "", "reload rejected", false,
                    "command", "", String.join("; ", ex.failures()));
            plugin.messages().send(sender, "reload-failed", Map.of("error", String.join("; ", ex.failures())));
            ex.failures().forEach(failure -> plugin.messages().styled(sender, " &c- " + failure));
            plugin.getLogger().severe("Reload of " + module + " rejected: " + String.join("; ", ex.failures()));
        } catch (Exception ex) {
            audit(sender, "config_reload", "module", module, "", "reload failed", false,
                    "command", "", rootMessage(ex));
            plugin.messages().send(sender, "reload-failed", Map.of("error", String.valueOf(ex.getMessage())));
            plugin.getLogger().severe("Reload of " + module + " failed: " + ex.getMessage());
        }
    }

    private void validate(CommandSender sender) {
        ValidationReport report = plugin.validation().validate();
        plugin.messages().sectionHeader(sender, "Configuration Validation", "&b");
        plugin.messages().field(sender, "Status", report.valid() ? "&a" : "&c",
                report.valid() ? "Passed" : "Failed");
        plugin.messages().field(sender, "Errors", report.errors().isEmpty() ? "&a" : "&c", report.errors().size());
        plugin.messages().field(sender, "Warnings", report.warnings().isEmpty() ? "&a" : "&e",
                report.warnings().size());
        report.errors().forEach(value -> plugin.messages().styled(sender, " &cERROR &8- &f" + value));
        report.warnings().forEach(value -> plugin.messages().styled(sender, " &eWARN &8- &f" + value));
        report.information().forEach(value -> plugin.messages().styled(sender, " &7INFO &8- &f" + value));
        plugin.messages().sectionFooter(sender);
    }

    private void diagnostic(CommandSender sender) {
        plugin.messages().warning(sender, "Creating a redacted diagnostic export...");
        plugin.diagnostics().export().whenComplete((path, error) -> sync(() -> {
            if (error != null) {
                audit(sender, "diagnostic_export", "diagnostics", "export", "", "failed", false,
                        "command", "", rootMessage(error));
                plugin.messages().error(sender, "Diagnostic export failed: &f" + rootMessage(error));
            } else {
                audit(sender, "diagnostic_export", "diagnostics", "export", "", String.valueOf(path), true,
                        "command", "", "redacted=true");
                plugin.messages().success(sender, "Diagnostic export created: &f" + path);
            }
        }));
    }

    private void diagnose(CommandSender sender, String[] args) {
        boolean detailed = args.length >= 2 && List.of("detail", "detailed", "full").contains(args[1].toLowerCase(Locale.ROOT));
        if (detailed && !sender.hasPermission("relicprison.admin.diagnostic.sensitive")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        plugin.messages().warning(sender, "Collecting RelicPrison diagnostics...");
        plugin.diagnostics().collect(detailed).whenComplete((snapshot, error) -> sync(() -> {
            if (error != null) {
                plugin.messages().error(sender, "Diagnostics collection failed: &f" + rootMessage(error));
                return;
            }
            plugin.messages().sectionHeader(sender, "RelicPrison Diagnostics &8- &f"
                    + snapshot.values().getOrDefault("collection-completed-at", "now"), "&b");
            List<String> keys = detailed ? snapshot.values().keySet().stream().sorted().toList()
                    : List.of("lifecycle", "database-health", "database-queue", "connection-pool-health",
                    "dirty-profiles", "failed-saves", "profiles-loading", "timed-out-profile-loads",
                    "reset-queue", "active-resets", "failed-resets", "blocks-processed-this-tick",
                    "vault-health", "luckperms-health", "itemsadder-health", "advancedenchantments-health",
                    "placeholderapi-health", "incomplete-progression-transactions", "failed-progression-transactions",
                    "failed-reward-deliveries", "pending-offline-rewards", "failed-commands", "backup-status",
                    "last-successful-backup", "configuration-validation-errors");
            for (String key : keys) {
                plugin.messages().field(sender, displayKey(key), "&f",
                        snapshot.values().getOrDefault(key, "unavailable"));
            }
            plugin.messages().sectionFooter(sender);
        }));
    }

    private void backup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("relicprison.admin.backup")) { plugin.messages().send(sender, "no-permission"); return; }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("list")) {
            List<BackupView> values = new ArrayList<>(plugin.backups().backups());
            plugin.messages().sectionHeader(sender, "RelicPrison Backups &8(&f" + values.size() + "&8)", "&b");
            if (values.isEmpty()) plugin.messages().styled(sender, "&7No backups are available.");
            values.stream().limit(10).forEach(view -> plugin.messages().styled(sender, " &e" + view.id()
                    + " &8- &f" + view.type() + " &8| &b" + plugin.numbers().full(view.sizeBytes()) + " &7bytes"));
            if (values.size() > 10) plugin.messages().styled(sender, "&8Showing the newest 10 backups.");
            plugin.messages().sectionFooter(sender);
            return;
        }
        if (action.equals("create")) {
            String type = args.length >= 3 ? args[2] : "full";
            plugin.messages().warning(sender, "Creating a &f" + type + " &ebackup...");
            plugin.backups().create(type).whenComplete((view, error) -> sync(() -> {
                if (error != null) {
                    audit(sender, "backup_create", "backup", type, "", "failed", false,
                            "command", "", rootMessage(error));
                    plugin.messages().error(sender, "Backup creation failed: &f" + rootMessage(error));
                } else {
                    audit(sender, "backup_create", "backup", view.id(), "", "created", true,
                            "command", view.id(), "type=" + view.type());
                    plugin.messages().success(sender, "Backup &f" + view.id() + " &awas created successfully.");
                }
            }));
            return;
        }
        if (action.equals("info")) {
            if (args.length < 3) { plugin.messages().usage(sender, "/rp backup info <id>"); return; }
            var view = plugin.backups().info(args[2]);
            if (view.isEmpty()) {
                plugin.messages().error(sender, "Backup &f" + args[2] + " &cwas not found.");
                return;
            }
            BackupView backup = view.get();
            plugin.messages().sectionHeader(sender, "Backup " + backup.id(), "&b");
            plugin.messages().field(sender, "Type", "&f", backup.type());
            plugin.messages().field(sender, "Created", "&f", java.time.Instant.ofEpochMilli(backup.createdAt()));
            plugin.messages().field(sender, "Size", "&b", plugin.numbers().full(backup.sizeBytes()) + " bytes");
            plugin.messages().field(sender, "Checksum", "&f", backup.checksum());
            plugin.messages().sectionFooter(sender);
            return;
        }
        if (action.equals("verify")) {
            if (args.length < 3) { plugin.messages().usage(sender, "/rp backup verify <id>"); return; }
            plugin.messages().warning(sender, "Verifying backup &f" + args[2] + "&e...");
            plugin.backups().verify(args[2]).whenComplete((report, error) -> sync(() -> {
                if (error != null) {
                    audit(sender, "backup_verify", "backup", args[2], "", "failed", false,
                            "command", args[2], rootMessage(error));
                    plugin.messages().error(sender, "Backup verification failed: &f" + rootMessage(error));
                    return;
                }
                audit(sender, "backup_verify", "backup", args[2], "", report.valid() ? "valid" : "invalid",
                        report.valid(), "command", args[2], "errors=" + report.errors().size());
                sendVerification(sender, report);
            }));
            return;
        }
        if (action.equals("delete")) {
            if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
                plugin.messages().usage(sender, "/rp backup delete <id> confirm");
                return;
            }
            plugin.backups().delete(args[2], true).whenComplete((deleted, error) -> sync(() -> {
                if (error != null) {
                    audit(sender, "backup_delete", "backup", args[2], "", "failed", false,
                            "command", args[2], rootMessage(error));
                    plugin.messages().error(sender, "Backup deletion failed: &f" + rootMessage(error));
                } else {
                    audit(sender, "backup_delete", "backup", args[2], "", deleted ? "deleted" : "missing", true,
                            "command", args[2], "confirmed=true");
                    if (deleted) plugin.messages().success(sender, "Backup &f" + args[2] + " &awas deleted.");
                    else plugin.messages().warning(sender, "Backup metadata was updated; the archive was already missing.");
                }
            }));
            return;
        }
        if (action.equals("restore")) {
            if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
                plugin.messages().usage(sender, "/rp backup restore <id> confirm"); return;
            }
            plugin.backups().requestRestoreVerified(args[2]).whenComplete((scheduled, error) -> sync(() -> {
                if (error != null) {
                    audit(sender, "backup_restore_schedule", "backup", args[2], "", "failed", false,
                            "command", args[2], rootMessage(error));
                    plugin.messages().error(sender, "Unable to schedule the restore: &f" + rootMessage(error));
                } else if (!scheduled) {
                    plugin.messages().error(sender, "Backup &f" + args[2] + " &cwas not found.");
                } else {
                    audit(sender, "backup_restore_schedule", "backup", args[2], "", "scheduled", true,
                            "command", args[2], "verified=true");
                    plugin.messages().warning(sender, "Restore verified and scheduled successfully.");
                    plugin.messages().styled(sender, "&7Fully restart the server to apply backup &f" + args[2] + "&7.");
                }
            }));
            return;
        }
        plugin.messages().usage(sender, "/rp backup <list|create|info|verify|delete|restore>");
    }

    private void sendVerification(CommandSender sender, BackupVerificationReport report) {
        plugin.messages().sectionHeader(sender, "Backup Verification", "&b");
        plugin.messages().field(sender, "Backup", "&f", report.backupId());
        plugin.messages().field(sender, "Status", report.valid() ? "&a" : "&c",
                report.valid() ? "Passed" : "Failed");
        report.errors().forEach(error -> plugin.messages().styled(sender, " &cERROR &8- &f" + error));
        report.warnings().forEach(warning -> plugin.messages().styled(sender, " &eWARN &8- &f" + warning));
        plugin.messages().field(sender, "Archive Checksum", "&f", report.archiveChecksum());
        plugin.messages().sectionFooter(sender);
    }

    private void auditList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("relicprison.admin.audit")) { plugin.messages().send(sender, "no-permission"); return; }
        AuditQuery query = plugin.audit().parseQuery(args, 1);
        plugin.audit().query(query).whenComplete((entries, error) -> sync(() -> {
            if (error != null) {
                plugin.messages().error(sender, "Audit query failed: &f" + rootMessage(error));
                return;
            }
            plugin.messages().sectionHeader(sender, "Audit Entries &8- &fPage " + query.page()
                    + " &8(&f" + entries.size() + "&8)", "&b");
            if (entries.isEmpty()) plugin.messages().styled(sender, "&7No audit entries matched the query.");
            for (AuditEntry entry : entries) {
                plugin.messages().styled(sender, " &f" + java.time.Instant.ofEpochMilli(entry.timestamp())
                        + " &8| &b" + entry.staffName() + " &8| &f" + entry.actionType() + " &8| &7"
                        + entry.targetType() + "/&f" + entry.targetId() + " &8| "
                        + (entry.success() ? "&aSuccess" : "&cFailed") + " &8| &7Related: &f" + entry.relatedId());
            }
            plugin.messages().sectionFooter(sender);
        }));
    }

    private void repair(CommandSender sender, String[] args) {
        if (args.length < 2) { plugin.messages().usage(sender, "/rp repair <player|all>"); return; }
        if (args[1].equalsIgnoreCase("all")) {
            var profiles = plugin.playerProfiles().cachedProfiles();
            var futures = profiles.stream().map(profile -> plugin.progression().repair(profile.uuid(), "admin-all", false)).toArray(java.util.concurrent.CompletableFuture[]::new);
            java.util.concurrent.CompletableFuture.allOf(futures).whenComplete((ignored, error) -> sync(() -> {
                if (error != null) plugin.messages().error(sender, "Progression repair failed: &f" + rootMessage(error));
                else {
                    audit(sender, "progression_repair", "player", "all-loaded", "", "repaired", true,
                            "command", "", "count=" + profiles.size());
                    plugin.messages().success(sender, "Repaired progression for &b" + profiles.size()
                            + " &aloaded profiles.");
                }
            }));
            return;
        }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null || plugin.playerProfiles().cachedProfile(target.getUniqueId()).isEmpty()) {
            plugin.messages().error(sender, "That player must be online or have a loaded profile."); return;
        }
        plugin.progression().repair(target.getUniqueId(), "admin", false).whenComplete((ignored, error) -> sync(() -> {
            if (error != null) {
                audit(sender, "progression_repair", "player", target.getUniqueId().toString(), "", "failed",
                        false, "command", "", rootMessage(error));
                plugin.messages().error(sender, "Progression repair failed: &f" + rootMessage(error));
            } else {
                audit(sender, "progression_repair", "player", target.getUniqueId().toString(), "", "repaired",
                        true, "command", "", "target=" + target.getName());
                plugin.messages().success(sender, "Repaired progression and LuckPerms groups for &f"
                        + target.getName() + "&a.");
            }
        }));
    }

    private void progressionTransaction(CommandSender sender, String[] args) {
        if (!sender.hasPermission("relicprison.admin.repair")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("list")) {
            plugin.progression().pendingTransactions(25).whenComplete((records, error) -> sync(() -> {
                if (error != null) {
                    plugin.messages().error(sender, "Unable to load progression transactions: &f" + rootMessage(error));
                    return;
                }
                plugin.messages().sectionHeader(sender, "Pending Progression Transactions &8(&f"
                        + records.size() + "&8)", "&d");
                if (records.isEmpty()) plugin.messages().styled(sender, "&7No pending progression transactions.");
                for (ProgressionTransactionRecord record : records) {
                    plugin.messages().styled(sender, " &e" + record.transactionId() + " &8| &f"
                            + record.playerId() + " &8| &d" + record.operationType() + " &8| &b" + record.state()
                            + " &8| &7Rewards: &f" + record.rewardStatus() + " &8| &7LP: &f"
                            + record.luckPermsStatus() + " &8| &7Attempts: &f" + record.recoveryAttempts());
                }
                plugin.messages().sectionFooter(sender);
            }));
            return;
        }
        if (args.length < 3) {
            plugin.messages().usage(sender, "/rp progression <list|info|retry> [transaction-id]");
            return;
        }
        if (action.equals("info")) {
            plugin.progression().transaction(args[2]).whenComplete((record, error) -> sync(() -> {
                if (error != null) {
                    plugin.messages().error(sender, "Unable to load the transaction: &f" + rootMessage(error));
                    return;
                }
                if (record.isEmpty()) {
                    plugin.messages().error(sender, "Progression transaction &f" + args[2] + " &cwas not found.");
                    return;
                }
                ProgressionTransactionRecord value = record.get();
                plugin.messages().sectionHeader(sender, "Progression Transaction", "&d");
                plugin.messages().field(sender, "Transaction ID", "&f", value.transactionId());
                plugin.messages().field(sender, "Player", "&f", value.playerId());
                plugin.messages().field(sender, "Operation", "&d", value.operationType());
                plugin.messages().field(sender, "State", "&b", value.state());
                plugin.messages().field(sender, "Rank", "&b",
                        value.previousRank() + " &8-> &b" + value.targetRank());
                plugin.messages().field(sender, "Prestige", "&d",
                        value.previousPrestige() + " &8-> &d" + value.targetPrestige());
                plugin.messages().field(sender, "Cost / Withdrawn", "&6",
                        value.expectedCost() + " &8/ &6" + value.actualWithdrawn());
                plugin.messages().field(sender, "Rewards / LuckPerms", "&f",
                        value.rewardStatus() + " &8/ &f" + value.luckPermsStatus());
                plugin.messages().field(sender, "Failure", "&c", value.failureSummary());
                plugin.messages().sectionFooter(sender);
            }));
            return;
        }
        if (action.equals("retry")) {
            plugin.progression().retryTransaction(args[2]).whenComplete((result, error) -> sync(() -> {
                if (error != null) {
                    audit(sender, "progression_transaction_retry", "transaction", args[2], "", "failed",
                            false, "command", args[2], rootMessage(error));
                    plugin.messages().error(sender, "Transaction retry failed: &f" + rootMessage(error));
                } else {
                    audit(sender, "progression_transaction_retry", "transaction", args[2], "", result.code(),
                            result.success(), "command", args[2], "");
                    if (result.success()) plugin.messages().success(sender, "Transaction retry completed: &f"
                            + result.code() + "&a.");
                    else plugin.messages().warning(sender, "Transaction retry completed with status &f"
                            + result.code() + "&e.");
                }
            }));
            return;
        }
        plugin.messages().usage(sender, "/rp progression <list|info|retry> [transaction-id]");
    }

    private void leaderboardRewards(CommandSender sender, String[] args) {
        if (!sender.hasPermission("relicprison.admin.leaderboard")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "pending";
        if (action.equals("preview")) {
            if (args.length < 3) {
                plugin.messages().usage(sender, "/rp rewards preview <board> [period-id]");
                return;
            }
            String period = args.length >= 4 ? args[3] : "";
            plugin.leaderboardRewards().preview(args[2], period).whenComplete((preview, error) -> sync(() -> {
                if (error != null) {
                    plugin.messages().error(sender, "Reward preview failed: &f" + rootMessage(error));
                    return;
                }
                plugin.messages().sectionHeader(sender, "Reward Preview &8- &f" + preview.boardId(), "&d");
                plugin.messages().field(sender, "Period", "&f", preview.periodId());
                plugin.messages().field(sender, "Winners", "&b", preview.winners().size());
                preview.winners().forEach(entry -> plugin.messages().styled(sender, " &e#" + entry.position()
                        + " &f" + entry.playerName() + " &8- &b" + entry.value()));
                plugin.messages().sectionFooter(sender);
            }));
            return;
        }
        if (action.equals("finalize") || action.equals("run")) {
            if (args.length < 4 || !args[args.length - 1].equalsIgnoreCase("confirm")) {
                plugin.messages().usage(sender, "/rp rewards finalize <board> [period-id] confirm");
                return;
            }
            String period = args.length >= 5 ? args[3] : "";
            UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
            plugin.leaderboardRewards().finalizePeriod(args[2], period, actor).whenComplete((preview, error) ->
                    sync(() -> {
                        if (error != null) {
                            plugin.messages().error(sender, "Reward finalization failed: &f" + rootMessage(error));
                            return;
                        }
                        String text = "Reward period &f" + preview.boardId() + "/" + preview.periodId()
                                + " &fis now &b" + preview.state() + "&a.";
                        if (preview.alreadyFinalized()) plugin.messages().warning(sender,
                                "Reward period &f" + preview.boardId() + "/" + preview.periodId()
                                        + " &ewas already finalized with state &f" + preview.state() + "&e.");
                        else plugin.messages().success(sender, text);
                        audit(sender, "leaderboard_reward_finalize", "leaderboard-period",
                                preview.boardId() + "/" + preview.periodId(), "", preview.state(), true,
                                "command", preview.periodId(), "already=" + preview.alreadyFinalized());
                    }));
            return;
        }
        if (action.equals("history")) {
            int limit = args.length >= 3 ? parseLimit(args[2]) : 25;
            plugin.leaderboardRewards().history(limit).whenComplete((records, error) -> sync(() -> {
                if (error != null) {
                    plugin.messages().error(sender, "Reward history query failed: &f" + rootMessage(error));
                    return;
                }
                plugin.messages().sectionHeader(sender, "Leaderboard Reward History &8(&f"
                        + records.size() + "&8)", "&d");
                if (records.isEmpty()) plugin.messages().styled(sender, "&7No reward history was found.");
                records.forEach(record -> plugin.messages().styled(sender, " &b" + record.boardId()
                        + " &8| &f" + record.periodId() + " &8| &e#" + record.finalPosition() + " &f"
                        + record.playerId() + " &8| &d" + record.rewardDefinitionId() + " &8| &b"
                        + record.state() + " &8| &7Attempts: &f" + record.attemptCount()));
                plugin.messages().sectionFooter(sender);
            }));
            return;
        }
        if (action.equals("pending")) {
            plugin.leaderboardRewards().pending(50).whenComplete((records, error) -> sync(() -> {
                if (error != null) {
                    plugin.messages().error(sender, "Pending reward query failed: &f" + rootMessage(error));
                    return;
                }
                plugin.messages().sectionHeader(sender, "Pending / Failed Rewards &8(&f"
                        + records.size() + "&8)", "&d");
                if (records.isEmpty()) plugin.messages().styled(sender, "&7No pending or failed rewards.");
                records.forEach(record -> plugin.messages().styled(sender, " &b" + record.boardId()
                        + " &8| &f" + record.periodId() + " &8| &f" + record.playerId() + " &8| &d"
                        + record.rewardDefinitionId() + " &8| &b" + record.state()));
                plugin.messages().sectionFooter(sender);
            }));
            return;
        }
        if (action.equals("retry")) {
            if (args.length < 6) {
                plugin.messages().usage(sender, "/rp rewards retry <board> <period-id> <uuid> <reward-id>");
                return;
            }
            UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
            try {
                UUID target = UUID.fromString(args[4]);
                plugin.leaderboardRewards().retry(args[2], args[3], target, args[5], actor)
                        .whenComplete((ignored, error) -> sync(() -> {
                            String targetId = args[2] + "/" + args[3] + "/" + args[4] + "/" + args[5];
                            if (error == null) {
                                audit(sender, "leaderboard_reward_retry", "leaderboard-reward", targetId,
                                        "", "queued", true, "command", args[3], "");
                                plugin.messages().success(sender, "Leaderboard reward retry was queued successfully.");
                            } else {
                                audit(sender, "leaderboard_reward_retry", "leaderboard-reward", targetId,
                                        "", "failed", false, "command", args[3], rootMessage(error));
                                plugin.messages().error(sender, "Reward retry failed: &f" + rootMessage(error));
                            }
                        }));
            } catch (IllegalArgumentException ex) {
                plugin.messages().error(sender, "Invalid player UUID &f" + args[4] + "&c.");
            }
            return;
        }
        plugin.messages().usage(sender, "/rp rewards <preview|finalize|history|pending|retry>");
    }

    private void exportPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) { plugin.messages().usage(sender, "/rp export <player>"); return; }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) { plugin.messages().error(sender, "No known player matched &f" + args[1] + "&c."); return; }
        plugin.dataTransfer().exportPlayer(target.getUniqueId()).whenComplete((path, error) -> sync(() -> {
            if (error != null) plugin.messages().error(sender, "Player export failed: &f" + rootMessage(error));
            else plugin.messages().success(sender, "Player export created: &f" + path);
        }));
    }

    private void importPlayer(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            plugin.messages().usage(sender, "/rp import <export-file.yml> confirm"); return;
        }
        Path file = Path.of(args[1]);
        plugin.dataTransfer().importPlayer(file, true).whenComplete((uuid, error) -> sync(() -> {
            if (error != null) {
                audit(sender, "data_import", "file", file.toString(), "", "failed", false,
                        "command", "", rootMessage(error));
                plugin.messages().error(sender, "Player import failed: &f" + rootMessage(error));
            } else {
                audit(sender, "data_import", "player", uuid.toString(), "", "imported", true,
                        "command", uuid.toString(), "file=" + file);
                plugin.messages().success(sender, "Imported player data for &f" + uuid + "&a.");
                plugin.messages().styled(sender, "&7LuckPerms groups will be repaired on the player's next join.");
            }
        }));
    }

    private OfflinePlayer findPlayer(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online;
        try { return Bukkit.getOfflinePlayer(UUID.fromString(input)); }
        catch (IllegalArgumentException ignored) { }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        return offline.hasPlayedBefore() || offline.isOnline() ? offline : null;
    }

    private void usage(CommandSender sender, String label) {
        CommandHelp.prisonAdmin(plugin.messages(), sender, 1, label);
    }

    private void usage(CommandSender sender, String label, int page) {
        CommandHelp.prisonAdmin(plugin.messages(), sender, page, label);
    }

    private void sync(Runnable task) { Bukkit.getScheduler().runTask(plugin, task); }
    private static String rootMessage(Throwable throwable) { Throwable current=throwable; while(current.getCause()!=null)current=current.getCause(); return String.valueOf(current.getMessage()); }

    private void audit(CommandSender sender, String action, String targetType, String targetId, String before,
                       String after, boolean success, String reason, String relatedId, String metadata) {
        if (plugin.audit() == null) return;
        plugin.audit().record(sender, action, targetType, targetId, before, after, success, reason,
                relatedId, metadata).exceptionally(error -> {
                    plugin.getLogger().warning("Unable to record staff audit entry: " + rootMessage(error));
                    return null;
                });
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], List.of("status", "menu", "admin", "reload", "validate", "diagnose", "diagnostic", "backup", "audit", "repair", "progression", "rewards", "export", "import", "help"));
        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) return match(args[1], List.of("config", "integrations", "messages", "mines", "ranks", "prestiges", "progression", "selling", "boosters", "mining", "custom-drops", "block-events", "leaderboards", "leaderboard-rewards", "gui", "backups", "all"));
        if (args.length == 2 && args[0].equalsIgnoreCase("diagnose")) return match(args[1], List.of("concise", "detail"));
        if (args.length == 2 && args[0].equalsIgnoreCase("backup")) return match(args[1], List.of("list", "create", "info", "verify", "delete", "restore"));
        if (args.length == 2 && args[0].equalsIgnoreCase("progression")) return match(args[1], List.of("list", "info", "retry"));
        if (args.length == 2 && args[0].equalsIgnoreCase("rewards")) return match(args[1], List.of("preview", "finalize", "history", "pending", "retry"));
        if (args.length == 3 && args[0].equalsIgnoreCase("backup") && args[1].equalsIgnoreCase("create")) return match(args[2], List.of("full", "config", "data"));
        if (args.length == 2 && (args[0].equalsIgnoreCase("repair") || args[0].equalsIgnoreCase("export"))) {
            List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            if (args[0].equalsIgnoreCase("repair")) { List<String> withAll = new ArrayList<>(players); withAll.add("all"); return match(args[1], withAll); }
            return match(args[1], players);
        }
        return List.of();
    }

    private static List<String> match(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value);
        return result;
    }

    private static int parseLimit(String raw) {
        try {
            return Math.max(1, Math.min(200, Integer.parseInt(raw)));
        } catch (NumberFormatException ex) {
            return 25;
        }
    }

    private static int page(String[] args, int index) {
        if (args.length <= index) return 1;
        try { return Integer.parseInt(args[index]); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private static String health(boolean healthy) {
        return healthy ? "&aConnected" : "&cUnavailable";
    }

    private static String displayKey(String key) {
        String[] parts = key.split("-");
        for (int index = 0; index < parts.length; index++) {
            if (!parts[index].isEmpty()) parts[index] = Character.toUpperCase(parts[index].charAt(0))
                    + parts[index].substring(1);
        }
        return String.join(" ", parts);
    }
}
