package site.mcrelicworld.relicprison.gui;

import org.bukkit.Material;

public record MineGuiConfig(String title, int size, boolean contextualMaterials, Material enabledMaterial,
                            Material disabledMaterial, Material fillerMaterial) {}
