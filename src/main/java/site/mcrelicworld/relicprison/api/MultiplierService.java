package site.mcrelicworld.relicprison.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Stable contract for deterministic rank, prestige, donor, and booster multipliers. */
public interface MultiplierService {
    /** Thread-safe. Returns the cached combined multiplier for a loaded or known player. */
    BigDecimal multiplier(UUID playerId);
}
