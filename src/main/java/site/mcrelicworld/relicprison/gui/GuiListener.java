package site.mcrelicworld.relicprison.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class GuiListener implements Listener {
    private final MineAdminGui gui;

    public GuiListener(MineAdminGui gui) { this.gui = gui; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MineAdminGui.Holder)) return;
        event.setCancelled(true);
        if (unsafe(event)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        HumanEntity clicker = event.getWhoClicked();
        if (clicker instanceof Player player) gui.handleClick(player, event.getCurrentItem());
    }

    @EventHandler
    public void onCreative(InventoryCreativeEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MineAdminGui.Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MineAdminGui.Holder) event.setCancelled(true);
    }

    private static boolean unsafe(InventoryClickEvent event) {
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        if (click.isShiftClick() || click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DOUBLE_CLICK || click == ClickType.CREATIVE || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP || click == ClickType.WINDOW_BORDER_LEFT
                || click == ClickType.WINDOW_BORDER_RIGHT || click == ClickType.MIDDLE) return true;
        return action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.DROP_ALL_CURSOR || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_CURSOR || action == InventoryAction.DROP_ONE_SLOT;
    }
}
