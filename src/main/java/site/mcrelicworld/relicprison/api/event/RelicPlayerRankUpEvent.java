package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public final class RelicPlayerRankUpEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String oldRank;
    private final String newRank;
    private final BigDecimal cost;
    private boolean cancelled;
    public RelicPlayerRankUpEvent(UUID playerId, String oldRank, String newRank, BigDecimal cost) {
        this.playerId = playerId; this.oldRank = oldRank; this.newRank = newRank; this.cost = cost;
    }
    public UUID playerId() { return playerId; }
    public String oldRank() { return oldRank; }
    public String newRank() { return newRank; }
    public BigDecimal cost() { return cost; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
