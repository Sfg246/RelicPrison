package site.mcrelicworld.relicprison.mining;

public enum BulkMiningTransactionState {
    CREATED,
    VALIDATED,
    BLOCKS_MUTATED,
    REWARD_DELIVERING,
    REWARD_DELIVERED,
    FINALIZING,
    COMMITTED,
    ROLLING_BACK,
    ROLLED_BACK,
    FAILED_RECOVERABLE,
    FAILED_PERMANENT
}
