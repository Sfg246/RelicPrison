package site.mcrelicworld.relicprison.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import site.mcrelicworld.relicprison.util.ColorUtil;

import java.util.List;
import java.util.UUID;

public final class GuiItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    private GuiItemBuilder(Material material) {
        item = new ItemStack(material);
        meta = item.getItemMeta();
    }

    public static GuiItemBuilder of(Material material) {
        return new GuiItemBuilder(material);
    }

    public GuiItemBuilder name(String value) {
        meta.setDisplayName(ColorUtil.color(value));
        return this;
    }

    public GuiItemBuilder lore(List<String> values) {
        meta.setLore(values.stream().map(ColorUtil::color).toList());
        return this;
    }

    public GuiItemBuilder glow(boolean enabled) {
        if (enabled) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public GuiItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM);
        return this;
    }

    public GuiItemBuilder amount(int value) {
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), value)));
        return this;
    }

    public GuiItemBuilder customModelData(Integer value) {
        if (value != null) meta.setCustomModelData(value);
        return this;
    }

    public GuiItemBuilder playerHead(UUID playerId) {
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
        return this;
    }

    public GuiItemBuilder stringData(NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
