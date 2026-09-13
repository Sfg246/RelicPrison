package site.mcrelicworld.relicprison.mining;

import java.math.BigDecimal;

/** Absolute reward targets used to make replay completion idempotent. */
public final class BulkRewardTarget {
    private BulkRewardTarget() { }

    public static BigDecimal remainingMoney(BigDecimal current, BigDecimal target, BigDecimal legacyAmount) {
        if (target == null) return legacyAmount == null ? BigDecimal.ZERO : legacyAmount;
        BigDecimal remaining = target.subtract(current == null ? BigDecimal.ZERO : current);
        return remaining.signum() <= 0 ? BigDecimal.ZERO : remaining;
    }

    public static int remainingExperience(int current, int target, int legacyAmount) {
        if (target < 0) return Math.max(0, legacyAmount);
        return Math.max(0, target - Math.max(0, current));
    }

    public static int targetExperience(int current, int amount) {
        long value = (long) Math.max(0, current) + Math.max(0, amount);
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
