package site.mcrelicworld.relicprison.progression;

import java.math.BigDecimal;
import java.util.UUID;

public record ProgressionTransactionRecord(
        String transactionId,
        UUID playerId,
        ProgressionOperationType operationType,
        String previousRank,
        String previousPrestige,
        String targetRank,
        String targetPrestige,
        BigDecimal expectedCost,
        BigDecimal actualWithdrawn,
        long createdAt,
        long updatedAt,
        ProgressionTransactionState state,
        int recoveryAttempts,
        String failureSummary,
        ProgressionRewardStatus rewardStatus,
        ProgressionExternalStatus luckPermsStatus,
        String idempotencyKey,
        String metadata
) {
    public boolean recoverableAutomatically() {
        if (state == ProgressionTransactionState.MANUAL_REVIEW || state == ProgressionTransactionState.STAFF_REVIEW
                || state == ProgressionTransactionState.FAILED || state.withdrawalAmbiguous()) {
            return false;
        }
        if (rewardStatus == ProgressionRewardStatus.RESERVED) return false;
        return state.needsRecovery();
    }
}
