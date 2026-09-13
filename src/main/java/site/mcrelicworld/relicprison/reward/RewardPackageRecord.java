package site.mcrelicworld.relicprison.reward;

import java.util.UUID;

public record RewardPackageRecord(
        String packageId,
        String sourceSystem,
        String sourceOperationId,
        UUID playerId,
        RewardComponentState state,
        long createdAt,
        long updatedAt,
        String frozenPayload,
        String failureSummary
) { }
