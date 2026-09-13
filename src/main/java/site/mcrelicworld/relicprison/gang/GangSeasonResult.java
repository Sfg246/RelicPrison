package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.util.UUID;

public record GangSeasonResult(String seasonId, String category, int position, UUID gangId,
                               String gangName, BigDecimal value, String frozenReward,
                               String rewardState, long recordedAt) { }
