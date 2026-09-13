package site.mcrelicworld.relicprison.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.gui.PrisonGuiManager;

public final class MenuCommand implements CommandExecutor {
    private final RelicPrisonPlugin plugin;
    private final PrisonGuiManager.Menu menu;

    public MenuCommand(RelicPrisonPlugin plugin, PrisonGuiManager.Menu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (menu == PrisonGuiManager.Menu.MAIN && args.length > 0 && args[0].equalsIgnoreCase("help")) {
            CommandHelp.prison(plugin.messages(), sender, page(args, 1));
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!plugin.ensureProfileReady(player)) return true;
        if (menu == PrisonGuiManager.Menu.LEADERBOARD && args.length > 0) {
            String period = args.length > 1 ? args[1] : "lifetime";
            plugin.prisonGuis().open(player, menu, 0, args[0] + ":" + period);
            return true;
        }
        plugin.prisonGuis().open(player, menu);
        return true;
    }

    private static int page(String[] args, int index) {
        if (args.length <= index) return 1;
        try { return Integer.parseInt(args[index]); }
        catch (NumberFormatException ignored) { return 1; }
    }
}
