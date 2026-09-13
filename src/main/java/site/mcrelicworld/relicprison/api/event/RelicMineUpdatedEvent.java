package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import site.mcrelicworld.relicprison.api.model.MineView;

/** Fired synchronously after a mine update is safely persisted. */
public final class RelicMineUpdatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MineView previous;
    private final MineView current;
    public RelicMineUpdatedEvent(MineView previous, MineView current) { this.previous = previous; this.current = current; }
    public MineView previous() { return previous; }
    public MineView current() { return current; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
