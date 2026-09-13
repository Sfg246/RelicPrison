package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.ProgressionResult;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ProgressionService {
    /** Thread-safe. Returns the cached rank for a loaded profile. */
    Optional<String> currentRank(UUID playerId);
    /** Thread-safe. Returns the cached prestige for a loaded profile. */
    Optional<String> currentPrestige(UUID playerId);
    /** Main-thread only. Returns a future and must not be joined on the main thread. */
    CompletableFuture<ProgressionResult> rankUp(UUID playerId, boolean maximum);
    /** Main-thread only. Returns a future and must not be joined on the main thread. */
    CompletableFuture<ProgressionResult> prestige(UUID playerId);
    /** Asynchronous. Returns a future and must not be joined on the main thread. */
    CompletableFuture<Void> repair(UUID playerId);
}
