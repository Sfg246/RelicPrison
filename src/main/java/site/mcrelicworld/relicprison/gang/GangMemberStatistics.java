package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.util.UUID;

public record GangMemberStatistics(
        UUID gangId,
        UUID playerId,
        long blocks,
        BigDecimal money,
        BigDecimal gangXp,
        long rankups,
        long prestiges,
        long blockEvents,
        long updatedAt
) {
    public GangMemberStatistics {
        money = money == null ? BigDecimal.ZERO : money;
        gangXp = gangXp == null ? BigDecimal.ZERO : gangXp;
    }
}
