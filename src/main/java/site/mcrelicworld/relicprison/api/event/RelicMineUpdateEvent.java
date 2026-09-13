package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import site.mcrelicworld.relicprison.api.model.MineView;

/** Fired synchronously before an existing mine definition is replaced. */
public final class RelicMineUpdateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MineView previous;
    private final MineView proposed;
    private boolean cancelled;
    public RelicMineUpdateEvent(MineView previous, MineView proposed) { this.previous = previous; this.proposed = proposed; }
    public MineView previous() { return previous; }
    public MineView proposed() { return proposed; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
