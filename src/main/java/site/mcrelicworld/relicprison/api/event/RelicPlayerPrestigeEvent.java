package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public final class RelicPlayerPrestigeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String oldPrestige;
    private final String newPrestige;
    private final BigDecimal cost;
    private boolean cancelled;
    public RelicPlayerPrestigeEvent(UUID playerId, String oldPrestige, String newPrestige, BigDecimal cost) {
        this.playerId = playerId; this.oldPrestige = oldPrestige; this.newPrestige = newPrestige; this.cost = cost;
    }
    public UUID playerId() { return playerId; }
    public String oldPrestige() { return oldPrestige; }
    public String newPrestige() { return newPrestige; }
    public BigDecimal cost() { return cost; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
