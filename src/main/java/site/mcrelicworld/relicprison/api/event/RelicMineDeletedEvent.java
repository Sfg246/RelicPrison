package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import site.mcrelicworld.relicprison.api.model.MineView;

/** Fired synchronously after a mine definition is deleted. */
public final class RelicMineDeletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MineView mine;
    public RelicMineDeletedEvent(MineView mine) { this.mine = mine; }
    public MineView mine() { return mine; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
