package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import site.mcrelicworld.relicprison.api.model.MineView;

/** Fired synchronously after a mine definition is safely persisted. */
public final class RelicMineCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MineView mine;
    public RelicMineCreatedEvent(MineView mine) { this.mine = mine; }
    public MineView mine() { return mine; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
