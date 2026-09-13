package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class RelicBoosterExpireEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String boosterId;
    private final UUID owner;
    public RelicBoosterExpireEvent(String boosterId, UUID owner) { this.boosterId = boosterId; this.owner = owner; }
    public String boosterId() { return boosterId; }
    public UUID owner() { return owner; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
