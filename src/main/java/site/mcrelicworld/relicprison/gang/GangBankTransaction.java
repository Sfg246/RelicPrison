package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.util.UUID;

public record GangBankTransaction(
        UUID id,
        UUID gangId,
        UUID playerId,
        BigDecimal amount,
        BigDecimal previousBalance,
        BigDecimal newBalance,
        Type type,
        String reason,
        long createdAt,
        String operationKey
) {
    public enum Type { DEPOSIT, WITHDRAWAL, UPGRADE_PURCHASE, MISSION_REWARD, ADMIN_ADJUSTMENT, DISBAND }
}
