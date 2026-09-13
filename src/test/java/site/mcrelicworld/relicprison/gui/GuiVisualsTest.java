package site.mcrelicworld.relicprison.gui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiVisualsTest {
    @Test
    void mineIconsRepresentProgressionAndLockedState() {
        assertEquals(Material.COAL_ORE, GuiVisuals.mineMaterial("coal", "Coal Mine", 0, true));
        assertEquals(Material.GOLD_ORE, GuiVisuals.mineMaterial("g", "Gold Mine", 6, true));
        assertEquals(Material.NETHERITE_BLOCK, GuiVisuals.mineMaterial("immortal", "Immortal", 32, true));
        assertEquals(Material.RED_STAINED_GLASS_PANE,
                GuiVisuals.mineMaterial("diamond", "Diamond Mine", 20, false));
    }

    @Test
    void rankAndPrestigeIconsProgressVisually() {
        assertNotEquals(GuiVisuals.rankMaterial(0, 26, false), GuiVisuals.rankMaterial(25, 26, false));
        assertEquals(Material.RED_STAINED_GLASS_PANE, GuiVisuals.rankMaterial(10, 26, true));
        assertEquals(Material.DIAMOND_ORE, GuiVisuals.prestigeMaterial("diamond", 4, false));
        assertEquals(Material.RED_STAINED_GLASS_PANE, GuiVisuals.prestigeMaterial("emerald", 5, true));
    }

    @Test
    void boosterEventAndPropertyIconsAreMeaningful() {
        assertEquals(Material.GOLD_INGOT, GuiVisuals.boosterMaterial("sell"));
        assertEquals(Material.EXPERIENCE_BOTTLE, GuiVisuals.boosterMaterial("mining_xp"));
        assertEquals(Material.BLAZE_POWDER, GuiVisuals.boosterMaterial("gang_xp"));
        assertEquals(Material.DIAMOND_PICKAXE, GuiVisuals.eventMaterial("mine_blocks"));
        assertEquals(Material.CLOCK, GuiVisuals.propertyMaterial("reset-interval"));
        assertEquals(Material.GOLD_INGOT, GuiVisuals.propertyMaterial("next-cost"));
    }

    @Test
    void stateInferenceAndGlowHaveConsistentMeaning() {
        assertEquals(GuiVisuals.State.CURRENT,
                GuiVisuals.inferState("&bCurrent Mine", List.of(), "none"));
        assertTrue(GuiVisuals.State.CURRENT.glow());
        assertTrue(GuiVisuals.State.ACTIVE.glow());
        assertTrue(GuiVisuals.State.ENABLED.glow());
        assertFalse(GuiVisuals.State.LOCKED.glow());
        assertFalse(GuiVisuals.State.DANGEROUS.glow());
    }

    @Test
    void namesAndLoreRemainColoredStructuredAndActionable() {
        assertEquals("&a&lMine A", GuiVisuals.styledName("&aMine A", GuiVisuals.State.CLICKABLE));
        List<String> lore = GuiVisuals.structuredLore(List.of("&7Warp to this mine.", "&7Rank: &fA"),
                GuiVisuals.State.CLICKABLE, "mine:a");
        assertEquals("&7Warp to this mine.", lore.getFirst());
        assertEquals("", lore.get(1));
        assertTrue(lore.contains("&eClick to warp."));
    }

    @Test
    void paginationBoundsAndNavigationSlotsArePredictable() {
        assertEquals(1, GuiLayout.pages(0, 36));
        assertEquals(2, GuiLayout.pages(37, 36));
        assertEquals(0, GuiLayout.boundedPage(-5, 3));
        assertEquals(2, GuiLayout.boundedPage(8, 3));
        assertEquals(45, GuiLayout.PAGED_54.previous());
        assertEquals(49, GuiLayout.PAGED_54.back());
        assertEquals(50, GuiLayout.PAGED_54.close());
        assertEquals(53, GuiLayout.PAGED_54.next());
    }

    @Test
    void confirmationsUseCancelInformationConfirmLayout() {
        assertEquals(10, GuiLayout.CONFIRM_27.cancel());
        assertEquals(13, GuiLayout.CONFIRM_27.information());
        assertEquals(16, GuiLayout.CONFIRM_27.confirm());
        assertNotEquals(GuiLayout.CONFIRM_27.cancel(), GuiLayout.CONFIRM_27.confirm());
    }

    @Test
    void gangMissionAndUpgradeStatesAreDistinct() {
        assertEquals(Material.WRITABLE_BOOK, GuiVisuals.missionMaterial("ACTIVE"));
        assertEquals(Material.LIME_WOOL, GuiVisuals.missionMaterial("COMPLETED"));
        assertEquals(Material.CHEST_MINECART, GuiVisuals.missionMaterial("CLAIMED"));
        assertEquals(GuiVisuals.State.CURRENT, GuiVisuals.upgradeState(true, true, true));
        assertEquals(GuiVisuals.State.LOCKED, GuiVisuals.upgradeState(false, false, true));
        assertEquals(GuiVisuals.State.DISABLED, GuiVisuals.upgradeState(false, true, false));
        assertEquals(GuiVisuals.State.CLICKABLE, GuiVisuals.upgradeState(false, true, true));
    }
}
