package site.mcrelicworld.relicprison.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class PrisonGuiListener implements Listener {
    private final PrisonGuiManager menus;
    public PrisonGuiListener(PrisonGuiManager menus){this.menus=menus;}
    @EventHandler public void onClick(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof PrisonGuiManager.Holder holder))return;
        event.setCancelled(true);
        if(unsafe(event))return;
        if(event.getRawSlot()<0||event.getRawSlot()>=event.getView().getTopInventory().getSize())return;
        if(event.getWhoClicked() instanceof Player player)menus.handle(player,event.getCurrentItem(),holder);
    }
    @EventHandler public void onCreative(InventoryCreativeEvent event){
        if(event.getView().getTopInventory().getHolder() instanceof PrisonGuiManager.Holder)event.setCancelled(true);
    }
    @EventHandler public void onDrag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof PrisonGuiManager.Holder)event.setCancelled(true);}

    @EventHandler public void onChat(AsyncChatEvent event){
        Player player=event.getPlayer();
        if(menus.hasPendingAdminInput(player.getUniqueId())){
            event.setCancelled(true);
            menus.handleAdminChatInput(player.getUniqueId(),player.getName(),plain(event));
            return;
        }
        if(menus.hasPendingGangRankInput(player.getUniqueId())){
            event.setCancelled(true);
            menus.handleGangRankChatInput(player.getUniqueId(),plain(event));
        }
    }

    private static String plain(AsyncChatEvent event){
        return PlainTextComponentSerializer.plainText().serialize(event.message());
    }

    private static boolean unsafe(InventoryClickEvent event){
        ClickType click=event.getClick();
        InventoryAction action=event.getAction();
        if(click.isShiftClick()||click==ClickType.NUMBER_KEY||click==ClickType.SWAP_OFFHAND
                ||click==ClickType.DOUBLE_CLICK||click==ClickType.CREATIVE||click==ClickType.DROP
                ||click==ClickType.CONTROL_DROP||click==ClickType.WINDOW_BORDER_LEFT
                ||click==ClickType.WINDOW_BORDER_RIGHT||click==ClickType.MIDDLE)return true;
        return action==InventoryAction.HOTBAR_SWAP||action==InventoryAction.COLLECT_TO_CURSOR
                ||action==InventoryAction.MOVE_TO_OTHER_INVENTORY
                ||action==InventoryAction.DROP_ALL_CURSOR||action==InventoryAction.DROP_ALL_SLOT
                ||action==InventoryAction.DROP_ONE_CURSOR||action==InventoryAction.DROP_ONE_SLOT;
    }
}
