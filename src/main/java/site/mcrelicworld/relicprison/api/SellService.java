package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.SellResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Stable contract for SellAll, AutoSell, and value estimates. Inventory calls must originate on the server thread. */
public interface SellService {
    /** Main-thread only. Returns a future and must not be joined on the main thread. */
    CompletableFuture<BigDecimal> sellInventory(UUID playerId);
    /** Main-thread only. Returns a future and must not be joined on the main thread. */
    CompletableFuture<SellResult> sellInventoryDetailed(UUID playerId);
    /** Main-thread only. Reads a live Bukkit inventory without blocking. */
    BigDecimal estimatedInventoryValue(UUID playerId);
    /** Main-thread only. Reads a live Bukkit inventory without blocking. */
    BigDecimal estimatedHeldValue(UUID playerId);
}
