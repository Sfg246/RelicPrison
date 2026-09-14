package site.mcrelicworld.relicprison.mining;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ToolDurabilityService {
    private final RelicPrisonPlugin plugin;
    private final MiningConfigRepository configs;
    private final Enchantment unbreaking;
    public ToolDurabilityService(RelicPrisonPlugin plugin, MiningConfigRepository configs) {
        this.plugin = plugin; this.configs = configs;
        this.unbreaking = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft("unbreaking"));
    }

    public boolean canUse(Player player, ItemStack tool) {
        if (tool == null || tool.getType().getMaxDurability() <= 0 || !(tool.getItemMeta() instanceof Damageable damageable)) return true;
        int remaining = tool.getType().getMaxDurability() - damageable.getDamage();
        MiningConfig config = configs.config();
        if (remaining <= config.minimumToolDurability()) {
            plugin.messages().send(player, "tool-protected", Map.of("remaining", Integer.toString(remaining)));
            return false;
        }
        if (remaining <= config.lowDurabilityWarning()) {
            plugin.messages().send(player, "tool-low-durability", Map.of("remaining", Integer.toString(remaining)));
        }
        return true;
    }

    public void damageBulk(Player player, ItemStack tool, int blockCount) {
        if (tool == null || tool.getType().getMaxDurability() <= 0 || !(tool.getItemMeta() instanceof Damageable damageable)) return;
        MiningConfig config = configs.config();
        int desired = switch (config.bulkDurabilityMode()) {
            case ONE_PER_OPERATION -> 1;
            case PER_BLOCK -> blockCount;
            case CAPPED -> Math.min(blockCount, config.bulkDurabilityCap());
        };
        int unbreakingLevel = unbreaking == null ? 0 : tool.getEnchantmentLevel(unbreaking);
        int applied = 0;
        for (int i = 0; i < desired; i++) {
            if (unbreakingLevel == 0 || ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) == 0) applied++;
        }
        if (applied <= 0) return;
        int maximumDamage = Math.max(0, tool.getType().getMaxDurability() - config.minimumToolDurability());
        damageable.setDamage(Math.min(maximumDamage, damageable.getDamage() + applied));
        tool.setItemMeta((ItemMeta) damageable);
        canUse(player, tool);
    }
}
