package site.mcrelicworld.relicprison.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.gang.GangBankTransaction;
import site.mcrelicworld.relicprison.gang.GangInvite;
import site.mcrelicworld.relicprison.gang.GangMissionState;
import site.mcrelicworld.relicprison.gang.GangOperationResult;
import site.mcrelicworld.relicprison.gang.GangRank;
import site.mcrelicworld.relicprison.gang.GangPermission;
import site.mcrelicworld.relicprison.gang.GangRepository;
import site.mcrelicworld.relicprison.gui.PrisonGuiManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class GangCommand implements CommandExecutor, TabCompleter {
    private final RelicPrisonPlugin plugin;
    private final Map<UUID, Long> disbandConfirmations = new ConcurrentHashMap<>();

    public GangCommand(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().error(sender, "This command can only be used by a player.");
            plugin.messages().styled(sender, "&7Staff can use &e/relicgang help &7for gang administration.");
            return true;
        }
        if (!plugin.ensureReady(sender)) return true;
        if (!plugin.gangs().config().enabled()) {
            plugin.messages().gangError(player, "Gangs are currently disabled by the server.");
            return true;
        }
        if (args.length == 0) {
            plugin.prisonGuis().open(player, PrisonGuiManager.Menu.GANG);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (subcommand) {
                case "create" -> create(player, args);
                case "invite" -> invite(player, args);
                case "invites" -> invites(player);
                case "accept" -> respondInvite(player, args, true);
                case "deny" -> respondInvite(player, args, false);
                case "join" -> join(player, args);
                case "leave" -> respond(player, plugin.gangs().leave(player.getUniqueId()));
                case "kick" -> target(player, args, target -> plugin.gangs().kick(player.getUniqueId(), target));
                case "promote" -> target(player, args, target -> plugin.gangs().changeRank(player.getUniqueId(), target, true));
                case "demote" -> target(player, args, target -> plugin.gangs().changeRank(player.getUniqueId(), target, false));
                case "transfer" -> target(player, args, target -> plugin.gangs().transfer(player.getUniqueId(), target));
                case "disband" -> disband(player, args);
                case "bank" -> bank(player, args);
                case "upgrade", "upgrades" -> upgrade(player, args);
                case "missions" -> missions(player, args);
                case "ranks" -> ranks(player);
                case "rank" -> rank(player, args);
                case "chat" -> chat(player, args);
                case "settings" -> settings(player, args);
                case "sethome" -> respond(player, plugin.gangs().setHome(player));
                case "home" -> sendResult(player, plugin.gangs().useHome(player));
                case "help" -> help(player, page(args, 1));
                default -> {
                    plugin.messages().gangError(player, "Unknown subcommand &f" + args[0] + "&c.");
                    plugin.messages().styled(player, "&7Use &e/gang help &7to view available commands.");
                }
            }
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().trim().startsWith("/")) {
                plugin.messages().gangUsage(player, ex.getMessage());
            } else {
                plugin.messages().gangError(player, rootMessage(ex));
            }
        }
        return true;
    }

    private void create(Player player, String[] args) {
        require(args, 3, "/gang create <name> <tag>");
        respond(player, plugin.gangs().create(player, args[1], args[2]));
    }

    private void invite(Player player, String[] args) {
        require(args, 2, "/gang invite <player>");
        respond(player, plugin.gangs().invite(player.getUniqueId(), playerId(args[1])));
    }

    private void invites(Player player) {
        plugin.gangs().invites(player.getUniqueId()).whenComplete((invites, error) -> sync(() -> {
            if (error != null) { plugin.messages().gangError(player, rootMessage(error)); return; }
            if (invites.isEmpty()) { plugin.messages().gangInfo(player, "You have no pending gang invites."); return; }
            plugin.messages().sectionHeader(player, "Pending Gang Invites", "&6");
            for (GangInvite invite : invites) {
                plugin.messages().styled(player, " &e" + invite.id() + " &8- &7Gang &f" + invite.gangId());
            }
            plugin.messages().sectionFooter(player);
        }));
    }

    private void respondInvite(Player player, String[] args, boolean accept) {
        require(args, 2, "/gang " + (accept ? "accept" : "deny") + " <invite-id>");
        if (accept && !player.hasPermission("relicprison.gang.join")) {
            plugin.messages().gangError(player, "You do not have permission to join gangs.");
            return;
        }
        UUID inviteId = UUID.fromString(args[1]);
        respond(player, plugin.gangs().respondInvite(player.getUniqueId(), inviteId, accept));
    }

    private void join(Player player, String[] args) {
        require(args, 2, "/gang join <name|tag>");
        if (!player.hasPermission("relicprison.gang.join")) {
            plugin.messages().gangError(player, "You do not have permission to join gangs.");
            return;
        }
        respond(player, plugin.gangs().joinOpen(player.getUniqueId(), args[1]));
    }

    private void disband(Player player, String[] args) {
        long now = System.currentTimeMillis();
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")
                || disbandConfirmations.getOrDefault(player.getUniqueId(), 0L) < now) {
            disbandConfirmations.put(player.getUniqueId(), now + 30_000L);
            plugin.messages().gangWarning(player, "This permanently disbands your gang and removes every active membership.");
            plugin.messages().styled(player, "&7Confirm within &f30 seconds &7with &e/gang disband confirm&7.");
            return;
        }
        disbandConfirmations.remove(player.getUniqueId());
        respond(player, plugin.gangs().disband(player.getUniqueId()));
    }

    private void bank(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("balance")) {
            plugin.gangs().cachedGang(player.getUniqueId()).ifPresentOrElse(
                    gang -> plugin.messages().gangInfo(player, "Gang Balance: &6"
                            + plugin.numbers().currency(gang.bankBalance())),
                    () -> plugin.messages().gangError(player, "You are not currently in a gang."));
            return;
        }
        if (args[1].equalsIgnoreCase("history")) {
            plugin.gangs().cachedGang(player.getUniqueId()).ifPresentOrElse(gang ->
                    plugin.gangs().repository().bankHistory(gang.id(), 10).whenComplete((history, error) -> sync(() -> {
                        if (error != null) { plugin.messages().gangError(player, rootMessage(error)); return; }
                        plugin.messages().sectionHeader(player, "Gang Bank History", "&6");
                        if (history.isEmpty()) plugin.messages().styled(player, "&7No bank transactions have been recorded.");
                        for (GangBankTransaction entry : history) plugin.messages().styled(player, " &b"
                                + entry.type() + " &6" + plugin.numbers().currency(entry.amount()) + " &8-> &6"
                                + plugin.numbers().currency(entry.newBalance()) + " &8(&7" + entry.reason() + "&8)");
                        plugin.messages().sectionFooter(player);
                    })), () -> plugin.messages().gangError(player, "You are not currently in a gang."));
            return;
        }
        require(args, 3, "/gang bank <deposit|withdraw> <amount> [reason]");
        BigDecimal amount = new BigDecimal(args[2]);
        String reason = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "command";
        if (args[1].equalsIgnoreCase("deposit")) respond(player, plugin.gangs().deposit(player, amount, reason));
        else if (args[1].equalsIgnoreCase("withdraw")) respond(player, plugin.gangs().withdraw(player, amount, reason));
        else throw new IllegalArgumentException("/gang bank <balance|deposit|withdraw|history>");
    }

    private void upgrade(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().sectionHeader(player, "Gang Upgrades", "&6");
            plugin.gangs().config().upgrades().values().forEach(upgrade -> plugin.messages().entry(player,
                    upgrade.id(), upgrade.displayName()));
            plugin.messages().styled(player, "&7Purchase one with &e/gang upgrade <upgrade>&7.");
            plugin.messages().sectionFooter(player);
            return;
        }
        respond(player, plugin.gangs().purchaseUpgrade(player.getUniqueId(), args[1]));
    }

    private void missions(Player player, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("claim")) {
            respond(player, plugin.gangs().claimMission(player.getUniqueId(), args[2]));
            return;
        }
        plugin.gangs().missions(player.getUniqueId()).whenComplete((missions, error) -> sync(() -> {
            if (error != null) { plugin.messages().gangError(player, rootMessage(error)); return; }
            if (missions.isEmpty()) { plugin.messages().gangInfo(player, "No gang missions are currently available."); return; }
            plugin.messages().sectionHeader(player, "Gang Missions", "&6");
            for (GangMissionState mission : missions) plugin.messages().styled(player, " &e" + mission.missionId()
                    + " &8- &b" + mission.progress().toPlainString() + "&8/&f" + mission.target().toPlainString()
                    + " &8[&f" + mission.state() + "&8]");
            plugin.messages().styled(player, "&7Claim completed rewards with &e/gang missions claim <mission>&7.");
            plugin.messages().sectionFooter(player);
        }));
    }

    private void ranks(Player player) {
        plugin.gangs().ranks(player.getUniqueId()).whenComplete((ranks, error) -> sync(() -> {
            if (error != null) { plugin.messages().gangError(player, rootMessage(error)); return; }
            plugin.messages().sectionHeader(player, "Gang Ranks", "&6");
            if (ranks.isEmpty()) plugin.messages().styled(player, "&7No gang ranks are available.");
            for (GangRank rank : ranks) plugin.messages().styled(player, " &b" + rank.displayName()
                    + " &8- &7Priority &f" + rank.priority() + " &8| &7Permissions: &f"
                    + String.join(", ", rank.permissions().stream().map(GangPermission::key).toList()));
            plugin.messages().sectionFooter(player);
        }));
    }

    private void rank(Player player, String[] args) {
        require(args, 2, "/gang rank <create|edit|permission|delete>");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                require(args, 5, "/gang rank create <name> <priority> <color>");
                plugin.gangs().createRank(player.getUniqueId(), args[2], Integer.parseInt(args[3]), args[4],
                        java.util.Set.of()).whenComplete((rank, error) -> sync(() -> {
                            if (error != null) plugin.messages().gangError(player, rootMessage(error));
                            else plugin.messages().gangSuccess(player, "Created rank &f" + rank.displayName()
                                    + " &8(&7ID: &f" + rank.id() + "&8)&a.");
                        }));
            }
            case "edit" -> {
                require(args, 6, "/gang rank edit <rank-id> <name> <priority> <color>");
                respond(player, plugin.gangs().editRank(player.getUniqueId(), UUID.fromString(args[2]), args[3],
                        Integer.parseInt(args[4]), args[5]));
            }
            case "permission" -> {
                require(args, 5, "/gang rank permission <rank-id> <permission> <true|false>");
                respond(player, plugin.gangs().setRankPermission(player.getUniqueId(), UUID.fromString(args[2]),
                        GangPermission.parse(args[3]), Boolean.parseBoolean(args[4])));
            }
            case "delete" -> {
                require(args, 4, "/gang rank delete <rank-id> <fallback-rank-id>");
                respond(player, plugin.gangs().deleteRank(player.getUniqueId(), UUID.fromString(args[2]),
                        UUID.fromString(args[3])));
            }
            default -> throw new IllegalArgumentException("/gang rank <create|edit|permission|delete>");
        }
    }

    private void settings(Player player, String[] args) {
        require(args, 3, "/gang settings <name|tag|description|color|motd|privacy> <value>");
        GangRepository.EditField field = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "name" -> GangRepository.EditField.NAME;
            case "tag" -> GangRepository.EditField.TAG;
            case "description" -> GangRepository.EditField.DESCRIPTION;
            case "color" -> GangRepository.EditField.COLOR;
            case "motd" -> GangRepository.EditField.MOTD;
            case "privacy" -> GangRepository.EditField.JOIN_MODE;
            default -> throw new IllegalArgumentException("Unknown gang setting");
        };
        String value = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        respond(player, plugin.gangs().edit(player.getUniqueId(), new GangRepository.Edit(field, value)));
    }

    private void chat(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("toggle")) {
            respond(player, plugin.gangs().toggleChat(player.getUniqueId()));
            return;
        }
        plugin.gangs().sendChat(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
    }

    private void target(Player player, String[] args,
                        java.util.function.Function<UUID, CompletableFuture<GangOperationResult>> action) {
        require(args, 2, "/gang " + args[0] + " <player>");
        respond(player, action.apply(playerId(args[1])));
    }

    private void help(Player player, int page) {
        CommandHelp.gang(plugin.messages(), player, page);
    }

    private void respond(Player player, CompletableFuture<GangOperationResult> future) {
        future.whenComplete((result, error) -> sync(() -> {
            if (error != null) plugin.messages().gangError(player, rootMessage(error));
            else sendResult(player, result);
        }));
    }

    private void sendResult(Player player, GangOperationResult result) {
        plugin.messages().gangResult(player, result.success(), result.message());
    }

    private void sync(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run(); else Bukkit.getScheduler().runTask(plugin, action);
    }

    private static void require(String[] args, int count, String usage) {
        if (args.length < count) throw new IllegalArgumentException(usage);
    }

    private static UUID playerId(String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return Bukkit.getOfflinePlayer(value).getUniqueId(); }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Gang operation failed" : current.getMessage();
    }

    private static int page(String[] args, int index) {
        if (args.length <= index) return 1;
        try { return Integer.parseInt(args[index]); }
        catch (NumberFormatException ignored) { return 1; }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return prefix(List.of("create", "invite", "invites", "accept", "deny", "join",
                "leave", "kick", "promote", "demote", "transfer", "disband", "bank", "upgrade", "missions",
                "ranks", "rank", "chat", "settings", "sethome", "home", "help"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("bank")) return prefix(
                List.of("balance", "deposit", "withdraw", "history"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("chat")) return prefix(List.of("toggle"), args[1]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("upgrade") || args[0].equalsIgnoreCase("upgrades"))) {
            return prefix(List.copyOf(plugin.gangs().config().upgrades().keySet()), args[1]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> values, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(normalized)).toList();
    }
}
