package site.mcrelicworld.relicprison.mining;

public final class BulkMiningRecoveryDecision {
    private BulkMiningRecoveryDecision() { }

    public static Action forState(BulkMiningTransactionState state) {
        if (state == null) return Action.ROLLBACK;
        return switch (state) {
            case REWARD_DELIVERED, FINALIZING -> Action.COMPLETE;
            case COMMITTED, ROLLED_BACK, FAILED_PERMANENT -> Action.NONE;
            default -> Action.ROLLBACK;
        };
    }

    public enum Action { ROLLBACK, COMPLETE, NONE }
}
