package site.mcrelicworld.relicprison.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.model.ProgressionResult;

import java.util.Map;

public final class RankupCommand implements CommandExecutor {
    private final RelicPrisonPlugin plugin;
    private final boolean maximum;

    public RankupCommand(RelicPrisonPlugin plugin, boolean maximum) {
        this.plugin = plugin;
        this.maximum = maximum;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        String permission = maximum ? "relicprison.rankupmax" : "relicprison.rankup";
        if (!player.hasPermission(permission)) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (!plugin.ensureProfileReady(player)) return true;
        plugin.progression().rankUp(player.getUniqueId(), maximum).whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().severe("Rankup failed for " + player.getName() + ": " + rootMessage(error));
                plugin.messages().send(player, "progression-error", Map.of("error", rootMessage(error)));
            } else if (!result.success()) sendFailure(player, result);
        });
        return true;
    }

    private void sendFailure(Player player, ProgressionResult result) {
        String key = switch (result.code()) {
            case "profile-loading" -> "database-unavailable";
            case "already-processing" -> "progression-processing";
            case "max-rank" -> "rankup-max-rank";
            case "not-enough-money" -> "rankup-insufficient";
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
