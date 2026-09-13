package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public class RelicSellEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final BigDecimal baseValue;
    private final BigDecimal finalValue;
    private boolean cancelled;
    public RelicSellEvent(UUID playerId, BigDecimal baseValue, BigDecimal finalValue) {
        this.playerId = playerId; this.baseValue = baseValue; this.finalValue = finalValue;
    }
    public UUID playerId() { return playerId; }
    public BigDecimal baseValue() { return baseValue; }
    public BigDecimal finalValue() { return finalValue; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
