package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RelicMineResetCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String mineId;
    private final String reason;
    private final long totalBlocks;
    private final long durationMillis;
    public RelicMineResetCompleteEvent(String mineId, String reason, long totalBlocks, long durationMillis) {
        this.mineId = mineId; this.reason = reason; this.totalBlocks = totalBlocks; this.durationMillis = durationMillis;
    }
    public String mineId() { return mineId; }
    public String reason() { return reason; }
    public long totalBlocks() { return totalBlocks; }
    public long durationMillis() { return durationMillis; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
