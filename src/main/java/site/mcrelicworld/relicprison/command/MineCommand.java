package site.mcrelicworld.relicprison.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.mine.MineDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MineCommand implements CommandExecutor, TabCompleter {
    private final RelicPrisonPlugin plugin;
    public MineCommand(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("relicprison.mine.teleport")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (!plugin.ensureProfileReady(player)) return true;
        plugin.mineTeleports().teleport(player, args.length == 0 ? null : args[0]);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player) || !plugin.isReady()) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (MineDefinition mine : plugin.mineService().mines()) {
            if (mine.id().startsWith(prefix) && plugin.mineAccess().canEnter(player.getUniqueId(), mine.id())) result.add(mine.id());
        }
        return result;
    }
}
