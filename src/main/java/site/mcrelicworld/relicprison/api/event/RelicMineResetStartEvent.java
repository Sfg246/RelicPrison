package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RelicMineResetStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String mineId;
    private final String reason;
    private final long totalBlocks;
    public RelicMineResetStartEvent(String mineId, String reason, long totalBlocks) { this.mineId = mineId; this.reason = reason; this.totalBlocks = totalBlocks; }
    public String mineId() { return mineId; }
    public String reason() { return reason; }
    public long totalBlocks() { return totalBlocks; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
