package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RelicMineResetPrepareEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String mineId;
    private final String reason;
    private boolean cancelled;
    public RelicMineResetPrepareEvent(String mineId, String reason) { this.mineId = mineId; this.reason = reason; }
    public String mineId() { return mineId; }
    public String reason() { return reason; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
