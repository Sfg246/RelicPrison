package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.util.UUID;

public record GangStatistics(
        UUID gangId,
        long blocks,
        BigDecimal money,
        BigDecimal gangXp,
        long rankups,
        long prestiges,
        long blockEvents,
        long updatedAt
) {
    public GangStatistics {
        money = money == null ? BigDecimal.ZERO : money;
        gangXp = gangXp == null ? BigDecimal.ZERO : gangXp;
    }
}
