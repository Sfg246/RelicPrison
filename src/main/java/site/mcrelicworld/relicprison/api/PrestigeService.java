package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.PrestigeView;

import java.util.Collection;
import java.util.Optional;

/** Stable contract for Coal through Immortal prestiges. */
public interface PrestigeService {
    /** Thread-safe. Returns an immutable prestige view if configured. */
    Optional<PrestigeView> prestige(String id);
    /** Thread-safe. Returns immutable prestige views in configured order. */
    Collection<PrestigeView> prestiges();
}
