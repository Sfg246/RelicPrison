package site.mcrelicworld.relicprison.api;

import java.util.UUID;

/** Stable contract for rank/prestige mine access checks. */
public interface MineAccessService {
    /** Thread-safe. Uses only loaded profile/catalog state. */
    boolean canEnter(UUID playerId, String mineId);
    /** Thread-safe. Uses only loaded profile/catalog state. */
    boolean canMine(UUID playerId, String mineId);
}
