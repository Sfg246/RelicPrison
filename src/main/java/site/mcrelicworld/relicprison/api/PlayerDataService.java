package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.PlayerProfileView;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Read-only player profile API. Mutations are owned by RelicPrison services. */
public interface PlayerDataService {
    /** Thread-safe. Returns the cached immutable/read-only profile view if loaded. */
    Optional<PlayerProfileView> cached(UUID playerId);
    /** Asynchronous. Returns a future and must not be joined on the main thread. */
    CompletableFuture<PlayerProfileView> load(UUID playerId, String playerName);
}
