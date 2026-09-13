package site.mcrelicworld.relicprison.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.gang.Gang;
import site.mcrelicworld.relicprison.gang.GangBankTransaction;
import site.mcrelicworld.relicprison.gang.GangOperationResult;
import site.mcrelicworld.relicprison.gang.GangRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RelicGangCommand implements CommandExecutor, TabCompleter {
    private final RelicPrisonPlugin plugin;

    public RelicGangCommand(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("relicprison.gang.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!plugin.ensureReady(sender)) return true;
        if (args.length == 0) { help(sender, 1); return true; }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "list", "search" -> search(sender, args);
                case "inspect" -> inspect(sender, args);
                case "disband" -> {
                    require(args, 3, "/relicgang disband <gang> confirm");
                    if (!args[2].equalsIgnoreCase("confirm")) throw new IllegalArgumentException(
                            "Disband requires: /relicgang disband <gang> confirm");
                    gangMutation(sender, args, "disband", gang -> plugin.gangs().repository()
                            .disband(gang.id(), actor(sender), System.currentTimeMillis()));
                }
                case "addmember", "add" -> member(sender, args, true);
                case "removemember", "remove" -> member(sender, args, false);
                case "setowner" -> setOwner(sender, args);
                case "setlevel" -> progression(sender, args, GangRepository.AdminNumericField.LEVEL, false);
                case "addlevel" -> progression(sender, args, GangRepository.AdminNumericField.LEVEL, true);
                case "setxp" -> progression(sender, args, GangRepository.AdminNumericField.XP, false);
                case "addxp" -> progression(sender, args, GangRepository.AdminNumericField.XP, true);
                case "setpoints" -> progression(sender, args, GangRepository.AdminNumericField.POINTS, false);
                case "addpoints" -> progression(sender, args, GangRepository.AdminNumericField.POINTS, true);
                case "adjustbank" -> adjustBank(sender, args);
                case "rename" -> edit(sender, args, GangRepository.EditField.NAME);
                case "tag" -> edit(sender, args, GangRepository.EditField.TAG);
                case "audit" -> audit(sender, args);
                case "season" -> season(sender, args);
                case "reload" -> reload(sender);
                case "help" -> help(sender, page(args, 1));
                default -> {
                    plugin.messages().error(sender, "Unknown gang administration subcommand &f" + args[0] + "&c.");
                    plugin.messages().styled(sender, "&7Use &e/relicgang help &7to view available commands.");
                }
            }
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().trim().startsWith("/")) {
                plugin.messages().usage(sender, ex.getMessage());
            } else {
                plugin.messages().error(sender, rootMessage(ex));
            }
        }
        return true;
    }

    private void search(CommandSender sender, String[] args) {
        String query = args.length > 1 ? args[1] : "";
        plugin.gangs().repository().search(query, 50).whenComplete((gangs, error) -> sync(() -> {
            if (error != null) { plugin.messages().error(sender, rootMessage(error)); return; }
            plugin.messages().sectionHeader(sender, "Gangs &8(&f" + gangs.size() + "&8)", "&6");
            if (gangs.isEmpty()) plugin.messages().styled(sender, "&7No gangs matched that search.");
            for (Gang gang : gangs) plugin.messages().styled(sender, " &6" + gang.name() + " &8[&f"
                    + gang.tag() + "&8] - &7Members &f" + gang.memberCount() + "&8/&f" + gang.memberLimit()
                    + " &8| " + (gang.enabled() ? "&aEnabled" : "&cDisabled") + " &8| &f" + gang.id());
            plugin.messages().sectionFooter(sender);
        }));
    }

    private void inspect(CommandSender sender, String[] args) {
        require(args, 2, "/relicgang inspect <name|tag>");
        resolve(args[1]).whenComplete((gang, error) -> sync(() -> {
            if (error != null) { plugin.messages().error(sender, rootMessage(error)); return; }
            plugin.messages().sectionHeader(sender, gang.name() + " &8[&f" + gang.tag() + "&8]", "&6");
            plugin.messages().field(sender, "Gang ID", "&f", gang.id());
            plugin.messages().field(sender, "Owner", "&f", gang.ownerId());
            plugin.messages().field(sender, "Level", "&b", gang.level());
            plugin.messages().field(sender, "Gang XP", "&d", plugin.numbers().full(gang.xp()));
            plugin.messages().field(sender, "Points", "&b", plugin.numbers().full(gang.points()));
            plugin.messages().field(sender, "Members", "&f", gang.memberCount() + "&8/&f" + gang.memberLimit());
            plugin.messages().field(sender, "Balance", "&6", plugin.numbers().currency(gang.bankBalance()));
            plugin.messages().field(sender, "Join Mode", "&b", gang.joinMode());
            plugin.messages().field(sender, "Status", gang.enabled() ? "&a" : "&c",
                    gang.enabled() ? "Enabled" : "Disabled");
            plugin.messages().sectionFooter(sender);
        }));
    }

    private void member(CommandSender sender, String[] args, boolean add) {
        require(args, 3, "/relicgang " + (add ? "addmember" : "removemember") + " <gang> <player>");
        OfflinePlayer player = Bukkit.getOfflinePlayer(args[2]);
        gangMutation(sender, args, add ? "add_member" : "remove_member", gang -> add
                ? plugin.gangs().repository().forceAddMember(gang.id(), player.getUniqueId(), actor(sender),
                        System.currentTimeMillis())
                : plugin.gangs().repository().removeMember(gang.id(), player.getUniqueId(), actor(sender), false,
                        System.currentTimeMillis()));
    }

    private void setOwner(CommandSender sender, String[] args) {
        require(args, 3, "/relicgang setowner <gang> <player>");
        OfflinePlayer player = Bukkit.getOfflinePlayer(args[2]);
        resolve(args[1]).thenCompose(gang -> plugin.gangs().repository().member(player.getUniqueId())
                .thenCompose(member -> {
                    CompletableFuture<GangOperationResult> joined = member.isPresent()
                            ? CompletableFuture.completedFuture(GangOperationResult.success("already member"))
                            : plugin.gangs().repository().forceAddMember(gang.id(), player.getUniqueId(), actor(sender),
                                    System.currentTimeMillis());
                    return joined.thenCompose(result -> result.success()
                            ? plugin.gangs().repository().transferOwnership(gang.id(), gang.ownerId(),
                                    player.getUniqueId(), System.currentTimeMillis())
                            : CompletableFuture.completedFuture(result));
                })).whenComplete((result, error) -> completeMutation(sender, "set_owner", args[1], result, error));
    }

    private void progression(CommandSender sender, String[] args, GangRepository.AdminNumericField field,
                             boolean additive) {
        require(args, 3, "/relicgang " + args[0] + " <gang> <amount>");
        BigDecimal value = new BigDecimal(args[2]);
        gangMutation(sender, args, args[0].toLowerCase(Locale.ROOT), gang ->
                plugin.gangs().repository().adjustProgression(gang.id(), actor(sender), field, value, additive,
                        System.currentTimeMillis()));
    }

    private void adjustBank(CommandSender sender, String[] args) {
        require(args, 3, "/relicgang adjustbank <gang> <signed-amount> [reason]");
        BigDecimal amount = new BigDecimal(args[2]);
        String reason = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                : "admin adjustment";
        resolve(args[1]).thenCompose(gang -> plugin.gangs().repository().changeBank(gang.id(), actor(sender), amount,
                GangBankTransaction.Type.ADMIN_ADJUSTMENT, reason, "gang-admin-bank:" + UUID.randomUUID(),
                BigDecimal.ZERO, BigDecimal.ZERO, "admin", System.currentTimeMillis())
                .thenApply(transaction -> GangOperationResult.success("Gang bank balance adjusted to &6"
                        + plugin.numbers().currency(transaction.newBalance()) + "&a")))
                .whenComplete((result, error) -> completeMutation(sender, "adjust_bank", args[1], result, error));
    }

    private void edit(CommandSender sender, String[] args, GangRepository.EditField field) {
        require(args, 3, "/relicgang " + args[0] + " <gang> <value>");
        String value = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        gangMutation(sender, args, args[0].toLowerCase(Locale.ROOT), gang -> plugin.gangs().repository().editGang(
                gang.id(), actor(sender), new GangRepository.Edit(field, value), System.currentTimeMillis()));
    }

    private void audit(CommandSender sender, String[] args) {
        require(args, 2, "/relicgang audit <gang>");
        resolve(args[1]).thenCompose(gang -> plugin.gangs().repository().audit(gang.id(), 30))
                .whenComplete((entries, error) -> sync(() -> {
                    if (error != null) { plugin.messages().error(sender, rootMessage(error)); return; }
                    plugin.messages().sectionHeader(sender, "Gang Audit", "&6");
                    if (entries.isEmpty()) plugin.messages().styled(sender, "&7No gang audit entries were found.");
                    entries.forEach(entry -> plugin.messages().styled(sender, " &f" + entry.createdAt()
                            + " &8| &b" + entry.actionType() + " &8| &7Actor &f" + entry.actorId()
                            + " &8| &7Target &f" + entry.targetId() + " &8| &7" + entry.reason()));
                    plugin.messages().sectionFooter(sender);
                }));
    }

    private void season(CommandSender sender, String[] args) {
        require(args, 3, "/relicgang season <create|finalize|results> <id> ...");
        if (!plugin.gangs().config().seasonsEnabled()) {
            throw new IllegalArgumentException("Gang seasons are disabled in gangs.yml");
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                require(args, 6, "/relicgang season create <id> <starts-epoch-ms> <ends-epoch-ms> <categories-csv> [reward-plan]");
                long starts = Long.parseLong(args[3]);
                long ends = Long.parseLong(args[4]);
                List<String> categories = List.of(args[5].split(","));
                String rewardPlan = args.length > 6 ? String.join(" ", java.util.Arrays.copyOfRange(args, 6, args.length))
                        : plugin.gangs().config().seasonRewardPlan();
                plugin.gangs().repository().createSeason(args[2], starts, ends, categories, rewardPlan,
                        System.currentTimeMillis()).whenComplete((season, error) -> sync(() -> {
                    plugin.audit().record(sender, "gang_admin_season_create", "gang_season", args[2], "",
                            error == null ? season.state().name() : "", error == null,
                            error == null ? "" : rootMessage(error), "", "categories=" + args[5]);
                    if (error != null) plugin.messages().error(sender, rootMessage(error));
                    else plugin.messages().success(sender, "Created gang season &f" + season.id() + "&a.");
                }));
            }
            case "finalize" -> plugin.gangs().repository().finalizeSeason(args[2], 100, System.currentTimeMillis())
                    .thenCompose(results -> plugin.gangs().recoverSeasonRewards().thenApply(ignored -> results))
                    .whenComplete((results, error) -> sync(() -> {
                        plugin.audit().record(sender, "gang_admin_season_finalize", "gang_season", args[2], "",
                                error == null ? "winners=" + results.size() : "", error == null,
                                error == null ? "" : rootMessage(error), "", "");
                        if (error != null) plugin.messages().error(sender, rootMessage(error));
                        else plugin.messages().success(sender, "Finalized gang season &f" + args[2]
                                + " &awith &b" + results.size() + " &afrozen results.");
                    }));
            case "results" -> plugin.gangs().repository().seasonResults(args[2]).whenComplete((results, error) -> sync(() -> {
                if (error != null) { plugin.messages().error(sender, rootMessage(error)); return; }
                plugin.messages().sectionHeader(sender, "Gang Season Results &8- &f" + args[2], "&6");
                if (results.isEmpty()) plugin.messages().styled(sender, "&7No frozen results are available.");
                results.forEach(result -> plugin.messages().styled(sender, " &b" + result.category() + " &8| &f#"
                        + result.position() + " &6" + result.gangName() + " &8| &f" + result.value()
                        + " &8| &7Reward: &f" + result.rewardState()));
                plugin.messages().sectionFooter(sender);
            }));
            default -> throw new IllegalArgumentException("/relicgang season <create|finalize|results>");
        }
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadModule("gangs");
            plugin.audit().record(sender, "gang_admin_reload", "config", "gangs", "", "reloaded", true,
                    "", "", "");
            plugin.messages().success(sender, "Gang configuration reloaded successfully.");
        } catch (Exception error) {
            plugin.audit().record(sender, "gang_admin_reload", "config", "gangs", "", "", false,
                    rootMessage(error), "", "");
            plugin.messages().error(sender, "Gang configuration reload failed: &f" + rootMessage(error));
        }
    }

    private void gangMutation(CommandSender sender, String[] args, String action,
                              java.util.function.Function<Gang, CompletableFuture<GangOperationResult>> operation) {
        require(args, 2, "/relicgang " + args[0] + " <gang>");
        resolve(args[1]).thenCompose(operation).whenComplete((result, error) ->
                completeMutation(sender, action, args[1], result, error));
    }

    private void completeMutation(CommandSender sender, String action, String target,
                                  GangOperationResult result, Throwable error) {
        sync(() -> {
            boolean success = error == null && result != null && result.success();
            String message = error == null && result != null ? result.message() : rootMessage(error);
            plugin.audit().record(sender, "gang_admin_" + action, "gang", target, "", success ? "updated" : "",
                    success, success ? "" : message, "", "");
            if (success) plugin.gangs().refreshAll();
            plugin.messages().gangResult(sender, success, message);
        });
    }

    private CompletableFuture<Gang> resolve(String value) {
        return plugin.gangs().repository().findByNameOrTag(value).thenCompose(gang -> gang
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> CompletableFuture.failedFuture(new IllegalArgumentException("Gang not found"))));
    }

    private static UUID actor(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    private void sync(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run(); else Bukkit.getScheduler().runTask(plugin, action);
    }

    private static void require(String[] args, int count, String usage) {
        if (args.length < count) throw new IllegalArgumentException(usage);
    }

    private static String rootMessage(Throwable error) {
        if (error == null) return "Gang operation failed";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Gang operation failed" : current.getMessage();
    }

    private void help(CommandSender sender, int page) {
        CommandHelp.gangAdmin(plugin.messages(), sender, page);
    }

    private static int page(String[] args, int index) {
        if (args.length <= index) return 1;
        try { return Integer.parseInt(args[index]); }
        catch (NumberFormatException ignored) { return 1; }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("list", "search", "inspect", "disband", "addmember", "removemember", "setowner",
                "setlevel", "addlevel", "setxp", "addxp", "setpoints", "addpoints", "adjustbank", "rename",
                "tag", "audit", "season", "reload", "help").stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
