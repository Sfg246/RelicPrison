package site.mcrelicworld.relicprison.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.model.SellResult;

import java.math.BigDecimal;
import java.util.Map;

public final class SellCommand implements CommandExecutor {
    private final RelicPrisonPlugin plugin;
    private final Mode mode;
    public SellCommand(RelicPrisonPlugin plugin, Mode mode) { this.plugin = plugin; this.mode = mode; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { plugin.messages().send(sender, "player-only", Map.of()); return true; }
        if (!player.hasPermission(mode.permission)) { plugin.messages().send(player, "no-permission", Map.of()); return true; }
        if (!plugin.ensureProfileReady(player)) return true;
        switch (mode) {
            case ALL -> sendResult(player, plugin.sellService().sellInventory(player, false));
            case HAND -> sendResult(player, plugin.sellService().sellHand(player));
            case VALUE -> {
                boolean hand = args.length > 0 && args[0].equalsIgnoreCase("hand");
                BigDecimal value = hand ? plugin.sellService().estimatedHeldValue(player.getUniqueId())
                        : plugin.sellService().estimatedInventoryValue(player.getUniqueId());
                plugin.messages().send(player, hand ? "sell-value-hand" : "sell-value-inventory",
                        Map.of("value", plugin.numbers().currency(value.doubleValue()),
                                "multiplier", plugin.multiplierService().multiplier(player.getUniqueId()).toPlainString()));
            }
        }
        return true;
    }

    private void sendResult(Player player, SellResult result) {
        if (!result.success()) {
            plugin.messages().send(player, result.error().equals("Nothing sellable") ? "sell-nothing" : "sell-failed",
                    Map.of("error", result.error()));
            return;
        }
        plugin.messages().send(player, "sell-success", Map.of(
                "amount", plugin.numbers().currency(result.finalValue().doubleValue()),
                "base", plugin.numbers().currency(result.baseValue().doubleValue()),
                "items", Integer.toString(result.itemCount()),
                "multiplier", plugin.multiplierService().multiplier(player.getUniqueId()).toPlainString()));
    }

    public enum Mode {
        ALL("relicprison.sellall"), HAND("relicprison.sellhand"), VALUE("relicprison.sellvalue");
        private final String permission;
        Mode(String permission) { this.permission = permission; }
    }
}
