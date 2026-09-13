package site.mcrelicworld.relicprison.gui;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Field;

public record GuiThemeConfig(
        Material filler,
        Material informationFiller,
        Material economyFiller,
        Material prestigeFiller,
        Material dangerFiller,
        Material adminFiller,
        Material unavailablePageMaterial,
        Material backMaterial,
        Material closeMaterial,
        Material confirmMaterial,
        Material cancelMaterial,
        Material deleteMaterial,
        Material searchMaterial,
        Sound clickSound,
        Sound successSound,
        Sound deniedSound,
        Sound teleportSound,
        Sound deleteSound,
        float soundVolume,
        float soundPitch
) {
    public static GuiThemeConfig defaults() {
        return new GuiThemeConfig(Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.YELLOW_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE,
                Material.GRAY_DYE, Material.ARROW, Material.BARRIER, Material.LIME_WOOL, Material.RED_WOOL,
                Material.TNT, Material.SPYGLASS, Sound.UI_BUTTON_CLICK, Sound.ENTITY_PLAYER_LEVELUP,
                Sound.BLOCK_NOTE_BLOCK_BASS, Sound.ENTITY_ENDERMAN_TELEPORT, Sound.ENTITY_ITEM_BREAK,
                0.65F, 1.0F);
    }

    public static GuiThemeConfig load(File file) throws Exception {
        GuiThemeConfig defaults = defaults();
        if (!file.exists()) return defaults;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return new GuiThemeConfig(
                material(yaml, "fillers.neutral", defaults.filler()),
                material(yaml, "fillers.information", defaults.informationFiller()),
                material(yaml, "fillers.economy", defaults.economyFiller()),
                material(yaml, "fillers.prestige", defaults.prestigeFiller()),
                material(yaml, "fillers.danger", defaults.dangerFiller()),
                material(yaml, "fillers.admin", defaults.adminFiller()),
                material(yaml, "navigation.unavailable-page.material", defaults.unavailablePageMaterial()),
                material(yaml, "navigation.back.material", defaults.backMaterial()),
                material(yaml, "navigation.close.material", defaults.closeMaterial()),
                material(yaml, "navigation.confirm.material", defaults.confirmMaterial()),
                material(yaml, "navigation.cancel.material", defaults.cancelMaterial()),
                material(yaml, "navigation.delete.material", defaults.deleteMaterial()),
                material(yaml, "navigation.search.material", defaults.searchMaterial()),
                sound(yaml, "sounds.click", defaults.clickSound()),
                sound(yaml, "sounds.success", defaults.successSound()),
                sound(yaml, "sounds.denied", defaults.deniedSound()),
                sound(yaml, "sounds.teleport", defaults.teleportSound()),
                sound(yaml, "sounds.delete", defaults.deleteSound()),
                (float) yaml.getDouble("sounds.volume", defaults.soundVolume()),
                (float) yaml.getDouble("sounds.pitch", defaults.soundPitch()));
    }

    private static Material material(YamlConfiguration yaml, String path, Material fallback) {
        Material material = Material.matchMaterial(yaml.getString(path, fallback.name()));
        if (material == null || !material.isItem()) throw new IllegalArgumentException(path + " is not an item material");
        return material;
    }

    private static Sound sound(YamlConfiguration yaml, String path, Sound fallback) {
        String configured = yaml.getString(path);
        if (configured == null || configured.isBlank()) return fallback;
        String name = configured.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            Field field = Sound.class.getField(name);
            Object value = field.get(null);
            if (value instanceof Sound sound) return sound;
        } catch (ReflectiveOperationException ignored) {
            throw new IllegalArgumentException(path + " is not a supported Paper sound: " + name);
        }
        throw new IllegalArgumentException(path + " is not a supported Paper sound: " + name);
    }
}
