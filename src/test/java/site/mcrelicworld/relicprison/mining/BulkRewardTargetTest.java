package site.mcrelicworld.relicprison.mining;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BulkRewardTargetTest {
    @Test
    void crashAfterXpDoesNotDuplicateReplay() {
        int target = BulkRewardTarget.targetExperience(100, 25);
        assertEquals(25, BulkRewardTarget.remainingExperience(100, target, 25));
        assertEquals(0, BulkRewardTarget.remainingExperience(125, target, 25));
        assertEquals(0, BulkRewardTarget.remainingExperience(150, target, 25));
    }

    @Test
    void crashAfterMoneyUsesOnlyUnpaidRemainder() {
        BigDecimal target = new BigDecimal("125.50");
        BigDecimal amount = new BigDecimal("25.50");
        assertEquals(amount, BulkRewardTarget.remainingMoney(new BigDecimal("100.00"), target, amount));
        assertEquals(BigDecimal.ZERO, BulkRewardTarget.remainingMoney(target, target, amount));
        assertEquals(new BigDecimal("5.50"),
                BulkRewardTarget.remainingMoney(new BigDecimal("120.00"), target, amount));
    }
}
