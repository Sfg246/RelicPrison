package site.mcrelicworld.relicprison.reward;

import java.math.BigDecimal;
import java.util.UUID;

public record RewardComponentRecord(
        String packageId,
        String componentId,
        String sourceSystem,
        String sourceOperationId,
        UUID playerId,
        String payload,
        RewardComponentType componentType,
        BigDecimal amount,
        long dueAt,
        RewardComponentState state,
        int attemptCount,
        long createdAt,
        long lastAttemptAt,
        long completedAt,
        String failureSummary,
        String idempotencyKey,
        String claimToken,
        String claimedBy,
        long claimedAt,
        long claimExpiresAt,
        long nextAttemptAt,
        long deliveredAt
) { }
