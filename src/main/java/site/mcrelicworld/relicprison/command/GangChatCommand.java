package site.mcrelicworld.relicprison.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

public final class GangChatCommand implements CommandExecutor {
    private final RelicPrisonPlugin plugin;

    public GangChatCommand(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().error(sender, "This command can only be used by a player.");
            return true;
        }
        if (!plugin.ensureReady(sender)) return true;
        if (!plugin.gangs().config().enabled()) {
            plugin.messages().gangError(player, "Gangs are currently disabled by the server.");
            return true;
        }
        if (args.length == 0) {
            respond(player, plugin.gangs().toggleChat(player.getUniqueId()));
            return true;
        }
        plugin.gangs().sendChat(player, String.join(" ", args));
        return true;
    }

    private void respond(Player player, java.util.concurrent.CompletableFuture<site.mcrelicworld.relicprison.gang.GangOperationResult> future) {
        future.whenComplete((result, error) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) plugin.messages().gangError(player, rootMessage(error));
            else plugin.messages().gangResult(player, result.success(), result.message());
        }));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Gang operation failed" : current.getMessage();
    }
}
