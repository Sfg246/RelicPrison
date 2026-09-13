package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import site.mcrelicworld.relicprison.api.model.BlockPosition;

import java.util.UUID;

public final class RelicMineBlockProcessEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String mineId;
    private final BlockPosition position;
    private final String source;
    private boolean cancelled;
    public RelicMineBlockProcessEvent(UUID playerId, String mineId, BlockPosition position, String source) {
        this.playerId = playerId; this.mineId = mineId; this.position = position; this.source = source;
    }
    public UUID playerId() { return playerId; }
    public String mineId() { return mineId; }
    public BlockPosition position() { return position; }
    public String source() { return source; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
