package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RelicMineResetFailEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String mineId;
    private final String reason;
    private final String error;
    public RelicMineResetFailEvent(String mineId, String reason, String error) { this.mineId = mineId; this.reason = reason; this.error = error; }
    public String mineId() { return mineId; }
    public String reason() { return reason; }
    public String error() { return error; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
