package site.mcrelicworld.relicprison.api;

/** Stable contract for the later shared normal/bulk mining pipeline. */
public interface MiningService {
    /** Thread-safe. Returns a monotonic processed block counter. */
    long processedBlocks();
    /** Thread-safe. Returns the current active mining operation count. */
    long activeOperations();
}
