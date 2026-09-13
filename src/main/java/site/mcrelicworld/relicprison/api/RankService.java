package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.RankView;

import java.util.Collection;
import java.util.Optional;

/** Stable contract for the A-Z rank catalog. */
public interface RankService {
    /** Thread-safe. Returns an immutable rank view if configured. */
    Optional<RankView> rank(String id);
    /** Thread-safe. Returns immutable rank views in configured order. */
    Collection<RankView> ranks();
}
