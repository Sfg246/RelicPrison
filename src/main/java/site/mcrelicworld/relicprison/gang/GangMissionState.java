package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.util.UUID;

public record GangMissionState(
        String instanceId,
        UUID gangId,
        String missionId,
        String periodKey,
        BigDecimal progress,
        BigDecimal target,
        State state,
        Long completedAt,
        Long claimedAt,
        long updatedAt
) {
    public enum State { ACTIVE, COMPLETED, CLAIMED, CANCELLED }
}
