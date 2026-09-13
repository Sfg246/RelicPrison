package site.mcrelicworld.relicprison.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BulkMiningRecoveryDecisionTest {
    @Test
    void partialRewardDeliveryAlwaysRollsBack() {
        assertEquals(BulkMiningRecoveryDecision.Action.ROLLBACK,
                BulkMiningRecoveryDecision.forState(BulkMiningTransactionState.REWARD_DELIVERING));
        assertEquals(BulkMiningRecoveryDecision.Action.ROLLBACK,
                BulkMiningRecoveryDecision.forState(BulkMiningTransactionState.ROLLING_BACK));
    }

    @Test
    void durableActualDeliveryAlwaysCompletes() {
        assertEquals(BulkMiningRecoveryDecision.Action.COMPLETE,
                BulkMiningRecoveryDecision.forState(BulkMiningTransactionState.REWARD_DELIVERED));
        assertEquals(BulkMiningRecoveryDecision.Action.COMPLETE,
                BulkMiningRecoveryDecision.forState(BulkMiningTransactionState.FINALIZING));
        assertEquals(BulkMiningRecoveryDecision.Action.NONE,
                BulkMiningRecoveryDecision.forState(BulkMiningTransactionState.COMMITTED));
    }
}
