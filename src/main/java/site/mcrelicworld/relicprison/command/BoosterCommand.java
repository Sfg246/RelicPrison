package site.mcrelicworld.relicprison.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.booster.ActiveBooster;
import site.mcrelicworld.relicprison.util.DurationParser;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BoosterCommand implements CommandExecutor, TabCompleter {
    private final RelicPrisonPlugin plugin;
    public BoosterCommand(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.ensureReady(sender)) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) return status(sender, args);
        if (args[0].equalsIgnoreCase("help")) {
            CommandHelp.booster(plugin.messages(), sender, page(args, 1));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("relicprison.admin.booster")) { plugin.messages().send(sender, "no-permission", Map.of()); return true; }
        try {
            return switch (sub) {
                case "give" -> give(sender, args);
                case "activate" -> activate(sender, args);
                case "remove" -> remove(sender, args);
                case "setmultiplier" -> setMultiplier(sender, args);
                case "list" -> list(sender);
                default -> { usage(sender); yield true; }
            };
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "booster-command-error", Map.of("error", ex.getMessage()));
            return true;
        }
    }

    private boolean status(CommandSender sender, String[] args) {
        UUID target;
        String name;
        if (args.length >= 2) {
            if (!sender.hasPermission("relicprison.admin.booster")) { plugin.messages().send(sender, "no-permission", Map.of()); return true; }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]); target = offline.getUniqueId(); name = offline.getName();
        } else if (sender instanceof Player player) { target = player.getUniqueId(); name = player.getName(); }
        else { plugin.messages().send(sender, "booster-status-player-required", Map.of()); return true; }
        var active = plugin.boosterService().activeFor(target);
        plugin.messages().sectionHeader(sender, "Boosters for " + (name == null ? target : name), "&b");
        plugin.messages().field(sender, "Final Multiplier", "&b",
                plugin.multiplierService().multiplier(target).toPlainString() + "x");
        if (active.isEmpty()) plugin.messages().styled(sender, "&7No active boosters.");
        for (var booster : active) plugin.messages().styled(sender, " &e" + booster.id() + " &8- &f"
                + (booster.serverWide() ? "Server" : "Personal") + " &8| &b"
                + booster.multiplier().toPlainString() + "x &8| &7"
                + DurationParser.format(booster.expiresAt() - System.currentTimeMillis()) + " remaining");
        plugin.messages().sectionFooter(sender);
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 5) { usage(sender); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) throw new IllegalArgumentException("Player must be online");
        boolean server = type(args[2]);
        BigDecimal multiplier = decimal(args[3]);
        Duration duration = DurationParser.parse(args[4]);
        ItemStack item = plugin.boosterItems().create(server, multiplier, duration.toMillis());
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        overflow.values().forEach(extra -> target.getWorld().dropItemNaturally(target.getLocation(), extra));
        audit(sender, "booster_give", target.getUniqueId().toString(), "", "item-created", true,
                "command", "type=" + (server ? "server" : "personal") + ",multiplier=" + multiplier);
        plugin.messages().send(sender, "booster-given", Map.of("player", target.getName(), "type", server ? "server" : "personal",
                "multiplier", multiplier.toPlainString(), "duration", DurationParser.format(duration.toMillis())));
        return true;
    }

    private boolean activate(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender); return true; }
        boolean server = args[1].equalsIgnoreCase("server") || args[1].equalsIgnoreCase("global");
        UUID owner = null;
        int multiplierIndex;
        int durationIndex;
        if (server) {
            multiplierIndex = 2; durationIndex = 3;
        } else if (args[1].equalsIgnoreCase("personal") || args[1].equalsIgnoreCase("player")) {
            if (args.length < 5) { usage(sender); return true; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            owner = target.getUniqueId();
            multiplierIndex = 3; durationIndex = 4;
        } else {
            throw new IllegalArgumentException("Type must be personal or server");
        }
        BigDecimal multiplier = decimal(args[multiplierIndex]);
        Duration duration = DurationParser.parse(args[durationIndex]);
        String activatedBy = sender instanceof Player player ? player.getUniqueId().toString() : "console";
        UUID finalOwner = owner;
        plugin.boosterService().activate(owner, server, multiplier, duration.toMillis(), activatedBy)
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || result == null || !result.success()) {
                        String failure = error != null ? root(error) : result == null ? "No activation result" : result.message();
                        audit(sender, "booster_activate", server ? "server" : String.valueOf(finalOwner), "",
                                "failed", false, failure,
                                "multiplier=" + multiplier);
                        plugin.messages().send(sender, "booster-activation-failed",
                                Map.of("error", failure));
                    } else {
                        if (server) plugin.multiplierService().invalidateAll();
                        else plugin.multiplierService().invalidate(finalOwner);
                        audit(sender, "booster_activate", server ? "server" : String.valueOf(finalOwner), "",
                                result.booster().id(), true, "command", "multiplier=" + multiplier);
                        plugin.messages().send(sender, "booster-activated", Map.of("type", server ? "server" : "personal",
                                "multiplier", multiplier.toPlainString(), "duration", DurationParser.format(duration.toMillis())));
                    }
                }));
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        plugin.boosterService().removeAll(target.getUniqueId()).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        audit(sender, "booster_remove", target.getUniqueId().toString(), "", "failed",
                                false, root(error), "");
                        plugin.messages().send(sender, "booster-command-error", Map.of("error", root(error)));
                    }
                    else {
                        plugin.multiplierService().invalidate(target.getUniqueId());
                        audit(sender, "booster_remove", target.getUniqueId().toString(), "", "removed",
                                true, "command", "");
                        plugin.messages().send(sender, "booster-removed", Map.of("player",
                                target.getName() == null ? args[1] : target.getName()));
                    }
                }));
        return true;
    }

    private boolean setMultiplier(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        BigDecimal multiplier = decimal(args[2]);
        BigDecimal before = plugin.boosterService().cachedPermanentMultiplier(target.getUniqueId());
        plugin.boosterService().setPermanentMultiplier(target.getUniqueId(), multiplier)
                .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        audit(sender, "booster_multiplier_edit", target.getUniqueId().toString(),
                                before.toPlainString(), "failed:" + multiplier, false, root(error), "");
                        plugin.messages().send(sender, "booster-command-error", Map.of("error", root(error)));
                    }
                    else {
                        plugin.multiplierService().invalidate(target.getUniqueId());
                        audit(sender, "booster_multiplier_edit", target.getUniqueId().toString(),
                                before.toPlainString(), multiplier.toPlainString(), true, "command", "");
                        plugin.messages().send(sender, "personal-multiplier-set", Map.of("player",
                                target.getName() == null ? args[1] : target.getName(), "multiplier", multiplier.toPlainString()));
                    }
                }));
        return true;
    }

    private boolean list(CommandSender sender) {
        var boosters = plugin.boosterService().serverBoosterRecords();
        plugin.messages().sectionHeader(sender, "Active Server Boosters", "&b");
        if (boosters.isEmpty()) plugin.messages().styled(sender, "&7No server boosters are active.");
        for (ActiveBooster booster : boosters) plugin.messages().styled(sender, " &e" + booster.id()
                + " &8- &b" + booster.multiplier().toPlainString() + "x &8| &7"
                + DurationParser.format(booster.expiresAt() - System.currentTimeMillis()) + " remaining");
        plugin.messages().sectionFooter(sender);
        return true;
    }

    private static boolean type(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "server", "global" -> true;
            case "personal", "player" -> false;
            default -> throw new IllegalArgumentException("Type must be personal or server");
        };
    }
    private static BigDecimal decimal(String value) {
        try { return new BigDecimal(value); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid multiplier: " + value); }
    }
    private void usage(CommandSender sender) {
        CommandHelp.booster(plugin.messages(), sender, 1);
    }
    private static String root(Throwable throwable) {
        Throwable current = throwable; while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    private void audit(CommandSender sender, String action, String targetId, String before, String after,
                       boolean success, String reason, String metadata) {
        if (plugin.audit() == null) return;
        plugin.audit().record(sender, action, "booster", targetId, before, after, success, reason,
                "", metadata).exceptionally(error -> {
                    plugin.getLogger().warning("Unable to record booster audit: " + root(error));
                    return null;
                });
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("status", "give", "activate", "remove", "setmultiplier", "list", "help"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("activate")) return filter(List.of("personal", "server"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return filter(List.of("personal", "server"), args[2]);
        return List.of();
    }
    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>();
        for (String value : values) if (value.startsWith(lower)) result.add(value);
        return result;
    }

    private static int page(String[] args, int index) {
        if (args.length <= index) return 1;
        try { return Integer.parseInt(args[index]); }
        catch (NumberFormatException ignored) { return 1; }
    }
}
