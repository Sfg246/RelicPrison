package site.mcrelicworld.relicprison.selection;

import org.bukkit.Location;
import site.mcrelicworld.relicprison.mine.Cuboid;

import java.util.UUID;

public final class SelectionSession {
    private final UUID playerId;
    private Location first;
    private Location second;
    private PendingAction pending;

    public SelectionSession(UUID playerId) { this.playerId = playerId; }
    public UUID playerId() { return playerId; }
    public Location first() { return first == null ? null : first.clone(); }
    public Location second() { return second == null ? null : second.clone(); }
    public void first(Location value) { first = value.clone(); }
    public void second(Location value) { second = value.clone(); }
    public boolean complete() { return first != null && second != null && first.getWorld() != null && second.getWorld() != null && first.getWorld().getUID().equals(second.getWorld().getUID()); }
    public Cuboid cuboid() { if (!complete()) throw new IllegalStateException("Selection is incomplete"); return Cuboid.of(first, second); }
    public PendingAction pending() { return pending; }
    public void pending(PendingAction value) { pending = value; }

    public record PendingAction(SelectionMode mode, String mineId, String sourceMineId, String displayName,
                                UUID worldId, String worldName, Cuboid bounds, long expiresAt) {
        public PendingAction(SelectionMode mode, String mineId, UUID worldId, String worldName, Cuboid bounds, long expiresAt) {
            this(mode, mineId, null, null, worldId, worldName, bounds, expiresAt);
        }
    }
}
