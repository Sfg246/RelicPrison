package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.MineView;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface MineService {
    /** Thread-safe. Returns an immutable mine view when loaded. */
    Optional<? extends MineView> findMine(String id);
    /** Thread-safe. Returns an immutable copy of loaded mines. */
    Collection<? extends MineView> mines();
    /** Thread-safe. Uses the current immutable spatial/catalog snapshot. */
    Optional<? extends MineView> mineAt(String worldName, int x, int y, int z);
    /** Thread-safe. Uses the current immutable spatial index. */
    Optional<? extends MineView> mineAt(UUID worldId, int x, int y, int z);
}
