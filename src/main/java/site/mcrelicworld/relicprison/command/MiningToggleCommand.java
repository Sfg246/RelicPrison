package site.mcrelicworld.relicprison.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.util.Map;

/**
 * Compatibility commands for old player-toggle aliases.
 * Mining automation is now controlled only by the server feature flags in config.yml.
 */
public final class MiningToggleCommand implements CommandExecutor {
    private final RelicPrisonPlugin plugin;
    private final Toggle toggle;

    public MiningToggleCommand(RelicPrisonPlugin plugin, Toggle toggle) {
        this.plugin = plugin;
        this.toggle = toggle;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only", Map.of());
            return true;
        }
        if (!player.hasPermission(toggle.permission)) {
            plugin.messages().send(player, "no-permission", Map.of());
            return true;
        }
        if (!plugin.ensureReady(player)) return true;
        if (!toggle.globallyEnabled(plugin)) {
            plugin.messages().send(player, "feature-disabled", Map.of("feature", toggle.display));
            return true;
        }
        plugin.messages().send(player, "toggle-server-controlled", Map.of("feature", toggle.display));
        return true;
    }

    public enum Toggle {
        AUTOSELL("AutoSell", "relicprison.autosell") {
            boolean globallyEnabled(RelicPrisonPlugin plugin) {
                return plugin.config().snapshot().features().autoSell();
            }
        },
        AUTOPICKUP("AutoPickup", "relicprison.autopickup") {
            boolean globallyEnabled(RelicPrisonPlugin plugin) {
                return plugin.config().snapshot().features().autoPickup();
            }
        },
        AUTOSMELT("AutoSmelt", "relicprison.autosmelt") {
            boolean globallyEnabled(RelicPrisonPlugin plugin) {
                return plugin.config().snapshot().features().autoSmelt();
            }
        },
        AUTOBLOCK("AutoBlock", "relicprison.autoblock") {
            boolean globallyEnabled(RelicPrisonPlugin plugin) {
                return plugin.config().snapshot().features().autoBlock();
            }
        };

        private final String display;
        private final String permission;

        Toggle(String display, String permission) {
            this.display = display;
            this.permission = permission;
        }

        abstract boolean globallyEnabled(RelicPrisonPlugin plugin);
    }
}
