package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.MineResetState;

import java.util.Optional;

public interface MineResetService {
    /** Thread-safe. Returns an immutable reset state snapshot. */
    Optional<MineResetState> state(String mineId);
    /** Thread-safe. Returns whether the mine is currently preparing/resetting/completing. */
    boolean isResetting(String mineId);
    /** Main-thread only. Queues a reset request without blocking. */
    boolean requestReset(String mineId, String reason, boolean warnings);
    /** Main-thread only. Cancels a queued or warning reset without blocking. */
    boolean cancelPendingReset(String mineId);
    /** Thread-safe. Returns cached remaining blocks, or -1 if unknown. */
    long remainingBlocks(String mineId);
    /** Thread-safe. Returns cached reset count, or -1 if unknown. */
    long resetCount(String mineId);
    /** Thread-safe. Returns cached next reset epoch millis, or -1 if unknown. */
    long nextReset(String mineId);
    /** Thread-safe. Returns cached mined percentage, or -1 if unknown. */
    double minedPercentage(String mineId);
}
