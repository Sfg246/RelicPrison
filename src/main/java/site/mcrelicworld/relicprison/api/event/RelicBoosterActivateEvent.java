package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public final class RelicBoosterActivateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String boosterId;
    private final UUID owner;
    private final BigDecimal multiplier;
    private final long expiresAt;
    private boolean cancelled;
    public RelicBoosterActivateEvent(String boosterId, UUID owner, BigDecimal multiplier, long expiresAt) {
        this.boosterId = boosterId; this.owner = owner; this.multiplier = multiplier; this.expiresAt = expiresAt;
    }
    public String boosterId() { return boosterId; }
    public UUID owner() { return owner; }
    public BigDecimal multiplier() { return multiplier; }
    public long expiresAt() { return expiresAt; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
