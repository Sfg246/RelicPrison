package site.mcrelicworld.relicprison.progression;

public enum ProgressionTransactionState {
    CREATED,
    VALIDATED,
    WITHDRAWAL_INTENT_RECORDED,
    WITHDRAWAL_IN_PROGRESS,
    WITHDRAWAL_CONFIRMED,
    WITHDRAWAL_AMBIGUOUS,
    PROFILE_UPDATE_PENDING,
    PERMISSIONS_UPDATE_PENDING,
    REWARDS_PENDING,
    STARTED,
    MONEY_WITHDRAWN,
    PROFILE_SAVED,
    PERMISSIONS_UPDATED,
    COMPLETED,
    COMPENSATION_PENDING,
    STAFF_REVIEW,
    FAILED,
    COMPENSATING,
    MANUAL_REVIEW;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == MANUAL_REVIEW || this == STAFF_REVIEW;
    }

    public boolean needsRecovery() {
        return !terminal() && this != COMPENSATING && this != COMPENSATION_PENDING
                && this != WITHDRAWAL_IN_PROGRESS && this != WITHDRAWAL_AMBIGUOUS;
    }

    public boolean withdrawalAmbiguous() {
        return this == WITHDRAWAL_IN_PROGRESS || this == WITHDRAWAL_AMBIGUOUS;
    }

    public boolean withdrawalConfirmed() {
        return this == WITHDRAWAL_CONFIRMED || this == MONEY_WITHDRAWN
                || this == PROFILE_UPDATE_PENDING || this == PROFILE_SAVED
                || this == PERMISSIONS_UPDATE_PENDING || this == PERMISSIONS_UPDATED
                || this == REWARDS_PENDING || this == COMPLETED;
    }
}
