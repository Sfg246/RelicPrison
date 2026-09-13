package site.mcrelicworld.relicprison.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.model.ProgressionResult;
import site.mcrelicworld.relicprison.progression.PrestigeDefinition;

import java.util.Map;

public final class PrestigeCommand implements CommandExecutor {
    private final RelicPrisonPlugin plugin;
    public PrestigeCommand(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("relicprison.prestige")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (!plugin.ensureProfileReady(player)) return true;
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            site.mcrelicworld.relicprison.database.PlayerProfile profile = plugin.playerProfiles()
                    .cachedProfile(player.getUniqueId()).orElse(null);
            if (profile == null) {
                plugin.messages().send(player, "database-unavailable");
                return true;
            }
            if (plugin.rankService().indexOf(profile.currentRank()) != plugin.rankService().definitions().size() - 1) {
                plugin.messages().send(player, "prestige-requires-z");
                return true;
            }
            String current = profile.currentPrestige();
            PrestigeDefinition next = plugin.prestigeService().next(current).orElse(null);
            if (next == null) {
                plugin.messages().send(player, "prestige-max");
                return true;
            }
            plugin.prestigeConfirmations().request(player.getUniqueId(),
                    plugin.config().snapshot().progression().prestigeConfirmationSeconds());
            plugin.messages().send(player, "prestige-confirm", Map.of("prestige", next.displayName(),
                    "cost", plugin.numbers().currency(next.cost()),
                    "seconds", String.valueOf(plugin.config().snapshot().progression().prestigeConfirmationSeconds())));
            return true;
        }
        if (!plugin.prestigeConfirmations().consume(player.getUniqueId())) {
            plugin.messages().send(player, "prestige-confirm-expired");
            return true;
        }
        plugin.progression().prestige(player.getUniqueId()).whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().severe("Prestige failed for " + player.getName() + ": " + rootMessage(error));
                plugin.messages().send(player, "progression-error", Map.of("error", rootMessage(error)));
            } else if (!result.success()) sendFailure(player, result);
        });
        return true;
    }

    private void sendFailure(Player player, ProgressionResult result) {
        String key = switch (result.code()) {
            case "profile-loading" -> "database-unavailable";
            case "already-processing" -> "progression-processing";
            case "requires-max-rank" -> "prestige-requires-z";
            case "max-prestige" -> "prestige-max";
            case "not-enough-money" -> "prestige-insufficient";
            case "cancelled" -> "progression-cancelled";
            default -> "progression-failed";
        };
        plugin.messages().send(player, key);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }
}
