package site.mcrelicworld.relicprison.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.progression.PrestigeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RelicPrestigeCommand implements CommandExecutor, TabCompleter {
    private final RelicPrisonPlugin plugin;
    public RelicPrestigeCommand(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("relicprison.admin.prestige")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) { usage(sender, label); return true; }
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
                    plugin.getLogger().severe("Unexpected prestige admin command failure for " + target.getUniqueId()
                            + ": " + rootMessage(ex));
                    plugin.messages().error(sender, "The prestige command failed unexpectedly. Check the console for details.");
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
            plugin.messages().sectionHeader(sender, "Prestige Status", "&d");
            plugin.messages().field(sender, "Player", "&f", target.getName());
            plugin.messages().field(sender, "Current Prestige", "&d",
                    profile.currentPrestige() == null ? "None" : profile.currentPrestige());
            plugin.messages().sectionFooter(sender);
            release(target);
            return;
        }
        String selected;
        if (action.equals("set")) {
            if (args.length < 3) {
                plugin.messages().usage(sender, "/relicprestige set <player> <prestige|none>");
                release(target);
                return;
            }
            selected = args[2].equalsIgnoreCase("none") ? null : plugin.prestigeService().definition(args[2]).map(PrestigeDefinition::id).orElse(null);
            if (selected == null && !args[2].equalsIgnoreCase("none")) {
                plugin.messages().error(sender, "Unknown prestige &f" + args[2] + "&c.");
                release(target);
                return;
            }
        } else if (action.equals("promote") || action.equals("demote")) {
            int amount = parseAmount(args);
            int current = plugin.prestigeService().indexOf(profile.currentPrestige());
            int conceptualCurrent = current;
            int direction = action.equals("promote") ? 1 : -1;
            int targetIndex = conceptualCurrent + direction * amount;
            if (targetIndex < 0) selected = null;
            else if (targetIndex >= plugin.prestigeService().definitions().size()) {
                plugin.messages().error(sender, "The requested prestige is outside the configured range.");
                release(target);
                return;
            } else selected = plugin.prestigeService().definitions().get(targetIndex).id();
        } else {
            usage(sender, "relicprestige");
            release(target);
            return;
        }
        String finalSelected = selected;
        String beforePrestige = profile.currentPrestige() == null ? "none" : profile.currentPrestige();
        plugin.progression().setPrestige(target.getUniqueId(), selected).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        audit(sender, "prestige_edit", target.getUniqueId().toString(), beforePrestige,
                                "failed:" + (finalSelected == null ? "none" : finalSelected), false,
                                rootMessage(error));
                        plugin.messages().error(sender, "Prestige update failed: &f" + rootMessage(error));
                    } else {
                        audit(sender, "prestige_edit", target.getUniqueId().toString(), beforePrestige,
                                finalSelected == null ? "none" : finalSelected, true, "command");
                        plugin.messages().success(sender, "Set &f" + target.getName() + "&a's prestige to &d"
                                + (finalSelected == null ? "None" : finalSelected) + "&a.");
                    }
                    release(target);
                }));
    }

    private void usage(CommandSender sender, String label) {
        CommandHelp.prestigeAdmin(plugin.messages(), sender, label);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], List.of("info", "set", "promote", "demote"));
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            List<String> values = new ArrayList<>();
            values.add("none");
            values.addAll(plugin.prestigeService().definitions().stream().map(PrestigeDefinition::id).toList());
            return match(args[2], values);
        }
        return List.of();
    }

    private int parseAmount(String[] args) {
        if (args.length < 3) return 1;
        try {
            int amount = Integer.parseInt(args[2]);
            if (amount < 1 || amount > plugin.prestigeService().definitions().size()) {
                throw new IllegalArgumentException("Amount is outside the prestige range");
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
                "", "command=relicprestige").exceptionally(error -> {
                    plugin.getLogger().warning("Unable to record prestige audit: " + rootMessage(error));
                    return null;
                });
    }
}
