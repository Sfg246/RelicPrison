package site.mcrelicworld.relicprison.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.util.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MineAdminGui {
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private void inventory(Inventory value) { this.inventory = value; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final RelicPrisonPlugin plugin;
    private final NamespacedKey mineIdKey;
    private final File configFile;
    private volatile MineGuiConfig config;

    public MineAdminGui(RelicPrisonPlugin plugin) throws Exception {
        this.plugin = plugin;
        this.mineIdKey = new NamespacedKey(plugin, "gui_mine_id");
        this.configFile = new File(plugin.getDataFolder(), "guis/mines.yml");
        this.config = preview();
    }

    public NamespacedKey mineIdKey() { return mineIdKey; }

    public MineGuiConfig preview() throws Exception {
        if (!configFile.exists()) plugin.saveResource("guis/mines.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(configFile);
        int size = yaml.getInt("size", 54);
        if (size < 9 || size > 54 || size % 9 != 0) throw new IllegalArgumentException("guis/mines.yml: size must be a multiple of 9 between 9 and 54");
        Material enabled = material(yaml.getString("enabled-mine-material", "DIAMOND_PICKAXE"), "enabled-mine-material");
        Material disabled = material(yaml.getString("disabled-mine-material", "RED_STAINED_GLASS_PANE"),
                "disabled-mine-material");
        Material filler = material(yaml.getString("filler-material", "BLACK_STAINED_GLASS_PANE"),
                "filler-material");
        return new MineGuiConfig(yaml.getString("title", "&5RelicPrison Mines"), size,
                yaml.getBoolean("contextual-mine-materials", true), enabled, disabled, filler);
    }

    public void apply(MineGuiConfig loaded) { config = loaded; }
    public MineGuiConfig config() { return config; }
    public void reload() throws Exception { apply(preview()); }

    public void open(Player player) {
        MineGuiConfig active = config;
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, active.size(), ColorUtil.component(active.title()));
        holder.inventory(inventory);
        ItemStack filler = GuiItemBuilder.of(active.fillerMaterial()).name("&8 ").build();
        for (int index = 0; index < inventory.getSize(); index++) inventory.setItem(index, filler);
        int slot = 0;
        for (MineDefinition mine : plugin.mineService().mines()) {
            if (slot >= inventory.getSize() - 9) break;
            Material icon = !mine.enabled() ? active.disabledMaterial() : active.contextualMaterials()
                    ? GuiVisuals.mineMaterial(mine.id(), mine.displayName(), slot, true) : active.enabledMaterial();
            List<String> lore = new ArrayList<>();
            lore.add("&7Administrative mine overview.");
            lore.add("");
            lore.add("&7ID: &f" + mine.id());
            lore.add("&7World: &f" + mine.worldName());
            lore.add("&7Volume: &f" + plugin.numbers().full(mine.volume()));
            lore.add("&7Blocks: &f" + mine.composition().entries().size());
            lore.add("");
            lore.add(mine.enabled() ? "&aEnabled" : "&cDisabled");
            lore.add("&eClick to teleport to the mine spawn.");
            ItemStack item = GuiItemBuilder.of(icon)
                    .name((mine.enabled() ? "&a&l" : "&c&l") + mine.displayName()
                            + (mine.enabled() ? "" : " &8[DISABLED]"))
                    .lore(lore).hideFlags().stringData(mineIdKey, mine.id()).build();
            inventory.setItem(slot++, item);
        }
        inventory.setItem(inventory.getSize() - 5, GuiItemBuilder.of(Material.MAP).name("&b&lMine Manager")
                .lore(List.of("&7Configured mines: &f" + plugin.mineService().mines().size(),
                        "", "&8Use /relicmine for destructive changes.")).build());
        inventory.setItem(inventory.getSize() - 1, GuiItemBuilder.of(Material.BARRIER).name("&c&lClose")
                .lore(List.of("&7Close the mine manager.")).build());
        // Paper returns InventoryView; the return value is intentionally ignored.
        player.openInventory(inventory);
    }

    private static Material material(String value, String path) {
        Material material = Material.matchMaterial(value);
        if (material == null || !material.isItem()) throw new IllegalArgumentException("guis/mines.yml: " + path + " must be a valid item material");
        return material;
    }

    public void handleClick(Player player, ItemStack item) {
        if (!player.hasPermission("relicprison.admin.mine")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (item == null || !item.hasItemMeta()) return;
        if (item.getType() == Material.BARRIER && ColorUtil.plain(item.getItemMeta().displayName())
                .equalsIgnoreCase("Close")) {
            player.closeInventory();
            return;
        }
        String mineId = item.getItemMeta().getPersistentDataContainer().get(mineIdKey, PersistentDataType.STRING);
        if (mineId == null) return;
        MineDefinition mine = plugin.mineService().findMine(mineId).orElse(null);
        if (mine == null) return;
        World world = Bukkit.getWorld(mine.worldId());
        if (world == null) {
            player.sendMessage(ColorUtil.color("&cThe mine world is not loaded."));
            return;
        }
        if (mine.spawn() != null) player.teleport(mine.spawn().toLocation(world));
        else player.teleport(world.getSpawnLocation());
    }
}
