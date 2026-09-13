package site.mcrelicworld.relicprison.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import site.mcrelicworld.relicprison.api.model.PlayerProfileView;

/** Fired synchronously after a player profile has been loaded asynchronously. */
public final class RelicPlayerDataLoadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PlayerProfileView profile;
    public RelicPlayerDataLoadEvent(PlayerProfileView profile) { this.profile = profile; }
    public PlayerProfileView profile() { return profile; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
