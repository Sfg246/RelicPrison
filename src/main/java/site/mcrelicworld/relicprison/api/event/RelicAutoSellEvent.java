package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public final class RelicAutoSellEvent extends RelicSellEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    public RelicAutoSellEvent(UUID playerId, BigDecimal baseValue, BigDecimal finalValue) {
        super(playerId, baseValue, finalValue);
    }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
