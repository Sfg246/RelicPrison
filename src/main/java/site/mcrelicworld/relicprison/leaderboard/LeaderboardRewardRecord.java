package site.mcrelicworld.relicprison.leaderboard;

import java.util.UUID;

public record LeaderboardRewardRecord(
        String boardId,
        String periodId,
        UUID playerId,
        int finalPosition,
        String rewardDefinitionId,
        RewardDeliveryState state,
        int attemptCount,
        long createdAt,
        long updatedAt,
        String failureSummary
) { }
