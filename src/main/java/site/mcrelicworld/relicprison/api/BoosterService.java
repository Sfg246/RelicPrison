package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.BoosterView;

import java.util.Collection;
import java.util.UUID;

/** Stable contract for personal and server-wide boosters. */
public interface BoosterService {
    /** Thread-safe. Returns immutable active booster views for the player and server. */
    Collection<BoosterView> activeFor(UUID playerId);
    /** Thread-safe. Returns immutable active server booster views. */
    Collection<BoosterView> activeServerBoosters();
}
