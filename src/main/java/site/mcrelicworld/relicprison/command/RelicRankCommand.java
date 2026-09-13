package site.mcrelicworld.relicprison.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.progression.RankDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RelicRankCommand implements CommandExecutor, TabCompleter {
    private final RelicPrisonPlugin plugin;
    public RelicRankCommand(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("relicprison.admin.rank")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            usage(sender, label);
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String name = target.getName() == null ? args[1] : target.getName();
        plugin.playerProfiles().load(target.getUniqueId(), name).whenComplete((view, loadError) -> {
            if (loadError != null) {
                plugin.messages().error(sender, "Unable to load player data: &f" + rootMessage(loadError));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    executeLoaded(sender, args, target);
                } catch (IllegalArgumentException ex) {
                    plugin.messages().error(sender, ex.getMessage());
                    release(target);
                } catch (RuntimeException ex) {
                    plugin.getLogger().severe("Unexpected rank admin command failure for " + target.getUniqueId()
                            + ": " + rootMessage(ex));
                    plugin.messages().error(sender, "The rank command failed unexpectedly. Check the console for details.");
                    release(target);
                }
            });
        });
        return true;
    }

    private void executeLoaded(CommandSender sender, String[] args, OfflinePlayer target) {
        PlayerProfile profile = plugin.playerProfiles().cachedProfile(target.getUniqueId()).orElse(null);
        if (profile == null) {
            plugin.messages().error(sender, "The player profile is not currently available.");
            release(target);
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("info")) {
            plugin.messages().sectionHeader(sender, "Rank Status", "&b");
            plugin.messages().field(sender, "Player", "&f", target.getName());
            plugin.messages().field(sender, "Current Rank", "&b", profile.currentRank());
            plugin.messages().sectionFooter(sender);
            release(target);
            return;
        }
        RankDefinition selected;
        if (action.equals("set")) {
            if (args.length < 3) {
                plugin.messages().usage(sender, "/relicrank set <player> <rank>");
                release(target);
                return;
            }
            selected = plugin.rankService().definition(args[2]).orElse(null);
        } else if (action.equals("promote") || action.equals("demote")) {
            int amount = parseAmount(args);
            int current = plugin.rankService().indexOf(profile.currentRank());
            int direction = action.equals("promote") ? 1 : -1;
            int targetIndex = Math.max(0, Math.min(plugin.rankService().definitions().size() - 1, current + direction * amount));
            selected = current < 0 || targetIndex == current ? null : plugin.rankService().definitions().get(targetIndex);
        } else {
            usage(sender, "relicrank");
            release(target);
            return;
        }
        if (selected == null) {
            plugin.messages().error(sender, "No valid target rank was found for that change.");
            release(target);
            return;
        }
        String beforeRank = profile.currentRank();
        plugin.progression().setRank(target.getUniqueId(), selected.id()).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        audit(sender, "rank_edit", target.getUniqueId().toString(), beforeRank,
                                "failed:" + selected.id(), false, rootMessage(error));
                        plugin.messages().error(sender, "Rank update failed: &f" + rootMessage(error));
                    } else {
                        audit(sender, "rank_edit", target.getUniqueId().toString(), beforeRank,
                                selected.id(), true, "command");
                        plugin.messages().success(sender, "Set &f" + target.getName() + " &ato rank &b"
                                + selected.displayName() + "&a.");
                    }
                    release(target);
                }));
    }

    private void usage(CommandSender sender, String label) {
        CommandHelp.rankAdmin(plugin.messages(), sender, label);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], List.of("info", "set", "promote", "demote"));
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return match(args[2], plugin.rankService().definitions().stream().map(RankDefinition::id).toList());
        }
        return List.of();
    }

    private int parseAmount(String[] args) {
        if (args.length < 3) return 1;
        try {
            int amount = Integer.parseInt(args[2]);
            if (amount < 1 || amount > plugin.rankService().definitions().size()) {
                throw new IllegalArgumentException("Amount is outside the rank range");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Amount must be a whole number", ex);
        }
    }

    private void release(OfflinePlayer target) {
        if (Bukkit.getPlayer(target.getUniqueId()) != null) return;
        plugin.playerProfiles().unload(target.getUniqueId()).exceptionally(error -> {
            plugin.getLogger().warning("Unable to unload admin-accessed profile " + target.getUniqueId() + ": " + rootMessage(error));
            return null;
        });
    }

    private static List<String> match(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value);
        return result;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    private void audit(CommandSender sender, String action, String targetId, String before, String after,
                       boolean success, String reason) {
        if (plugin.audit() == null) return;
        plugin.audit().record(sender, action, "player", targetId, before, after, success, reason,
                "", "command=relicrank").exceptionally(error -> {
                    plugin.getLogger().warning("Unable to record rank audit: " + rootMessage(error));
                    return null;
                });
    }
}
